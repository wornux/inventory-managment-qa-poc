package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuthorizationService authorizationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_rejectsRoleWithPermissionsActorDoesNotHave() {
        authenticateActor(AppPermission.USER_CREATE, AppPermission.USER_ASSIGN);
        Role elevatedRole = role(AppPermission.PRODUCT_DELETE);
        when(roleRepository.findAllById(Set.of(7L))).thenReturn(List.of(elevatedRole));

        var request = new UserRequest();
        request.setUsername("new-user");
        request.setEmail("new-user@example.com");
        request.setRoleIds(Set.of(7L));
        var service = new UserService(appUserRepository, roleRepository, new AuthorizationService(appUserRepository));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(UserException.class)
                .hasMessage("You cannot assign a role containing permissions that you do not have.");
    }

    @Test
    void reads_normalizeFiltersAuthorizeAndReportMissingUsers() {
        UserService service = service();
        var pageable = PageRequest.of(0, 10);
        var emptyPage = new PageImpl<AppUser>(List.of(), pageable, 0);
        when(appUserRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        assertThat(service.search(new UserFilter(" QUERY ", true), pageable)).isEmpty();
        assertThat(service.search(null, pageable)).isEmpty();
        assertThat(service.search(new UserFilter(null, null), pageable)).isEmpty();

        when(appUserRepository.findWithRolesById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOf(UserException.class)
                .hasMessage("User was not found.");
        AppUser found = new AppUser("u", "e", null, null);
        when(appUserRepository.findWithRolesById(2L)).thenReturn(Optional.of(found));

        assertThat(service.get(2L)).isSameAs(found);

        when(roleRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        assertThat(service.activeRoles()).isEmpty();
        verify(authorizationService, times(6)).check(AppPermission.USER_VIEW);
    }

    @Test
    void create_normalizesPersistsAndChecksUniquenessAndRoleAssignment() {
        Role role = UserDomainTest.role("R", true, AppPermission.PRODUCT_VIEW);
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role));
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(appUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        UserRequest request = request(" user ", " U@E.COM ", 1L);
        request.setActive(false);

        AppUser result = service().create(request);

        assertThat(result.getUsername()).isEqualTo("user");
        assertThat(result.getEmail()).isEqualTo("u@e.com");
        assertThat(result.isActive()).isFalse();

        when(appUserRepository.existsByUsernameIgnoreCase("user")).thenReturn(true);

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Username");
        when(appUserRepository.existsByUsernameIgnoreCase("user")).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCase("u@e.com")).thenReturn(true);

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void create_rejectsAbsentInactiveAndIncompleteRoleSelections() {
        UserRequest request = request(null, null);

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("one role");
        request.setRoleIds(null);

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("one role");
        UserRequest nullRoles = mock(UserRequest.class);
        when(nullRoles.getRoleIds()).thenReturn(null);

        assertThatThrownBy(() -> service().create(nullRoles))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("one role");
        request.setRoleIds(Set.of(1L));
        when(roleRepository.findAllById(Set.of(1L)))
                .thenReturn(List.of(UserDomainTest.role("R", false, AppPermission.USER_VIEW)));

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("one role");
    }

    @Test
    void update_enforcesOptimisticLockUniquenessAndUpdatesRoles() throws Exception {
        AppUser user = new AppUser("old", "old@e.com", null, null);
        set(user, "version", 2L);
        Role role = UserDomainTest.role("R", true, AppPermission.PRODUCT_VIEW);
        UserRequest request = request(" new ", " N@E.COM ", 1L);
        request.setVersion(2L);
        when(appUserRepository.findWithRolesById(7L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role));
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(appUserRepository.save(user)).thenReturn(user);

        assertThat(service().update(7L, request).getUsername()).isEqualTo("new");

        request.setVersion(3L);

        assertThatThrownBy(() -> service().update(7L, request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("another administrator");
        request.setVersion(2L);
        when(appUserRepository.existsByUsernameIgnoreCaseAndIdNot("new", 7L)).thenReturn(true);

        assertThatThrownBy(() -> service().update(7L, request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Username");
        when(appUserRepository.existsByUsernameIgnoreCaseAndIdNot("new", 7L)).thenReturn(false);
        when(appUserRepository.existsByEmailIgnoreCaseAndIdNot("n@e.com", 7L)).thenReturn(true);

        assertThatThrownBy(() -> service().update(7L, request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("Email");
        when(appUserRepository.findWithRolesById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(8L, request))
                .isInstanceOf(UserException.class)
                .hasMessage("User was not found.");
    }

    @Test
    void deactivate_rejectsSelfByUsernameOrEmailAndHandlesAuthenticationEdges() {
        AppUser user = new AppUser("User", "mail@example.com", null, null);
        when(appUserRepository.findWithRolesById(1L)).thenReturn(Optional.of(user));

        authenticate("user");

        assertThatThrownBy(() -> service().deactivate(1L))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("own account");
        authenticate("MAIL@example.com");

        assertThatThrownBy(() -> service().deactivate(1L)).isInstanceOf(UserException.class);

        SecurityContextHolder.clearContext();

        service().deactivate(1L);

        assertThat(user.isActive()).isFalse();

        user.setActive(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(null, "x"));

        service().deactivate(1L);

        Authentication noName = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(noName);

        service().deactivate(1L);

        when(appUserRepository.findWithRolesById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(2L)).isInstanceOf(UserException.class);
    }

    @Test
    void capabilityQueriesDelegateToAuthorization() {
        var create = Set.of(AppPermission.USER_CREATE, AppPermission.USER_ASSIGN);
        var update = Set.of(AppPermission.USER_UPDATE, AppPermission.USER_ASSIGN);
        when(authorizationService.canAll(create)).thenReturn(true);
        when(authorizationService.canAll(update)).thenReturn(false);
        when(authorizationService.can(AppPermission.USER_DELETE)).thenReturn(true);

        assertThat(service().canCreateUsers()).isTrue();
        assertThat(service().canUpdateUsers()).isFalse();
        assertThat(service().canDeleteUsers()).isTrue();
        verify(authorizationService).canAll(create);
        verify(authorizationService).canAll(update);
    }

    private UserService service() {
        return new UserService(appUserRepository, roleRepository, authorizationService);
    }

    private UserRequest request(String username, String email, Long... ids) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setRoleIds(new LinkedHashSet<>(List.of(ids)));

        return request;
    }

    private void authenticate(String name) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, "x"));
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);

        declared.set(target, value);
    }

    private void authenticateActor(AppPermission... permissions) {
        Role role = role(permissions);
        AppUser actor = new AppUser("admin", "admin@example.com", "issuer", "subject");
        actor.addRole(role);
        when(appUserRepository.findForAuthorization("admin")).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("admin", "password", List.of()));
    }

    private Role role(AppPermission... permissions) {
        Role role = new Role("TEST", "Test", null, false);
        role.update(role.getName(), role.getDescription(), true, Set.of(permissions));

        return role;
    }
}
