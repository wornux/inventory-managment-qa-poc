package com.wornux.user;

import static com.wornux.SpecificationTestSupport.predicateCount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.security.KeycloakAdminBootstrapProperties;
import com.wornux.security.KeycloakAdminClient;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private KeycloakAdminBootstrapProperties keycloakProperties;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_rejectsRoleWithPermissionsActorDoesNotHave() {
        authenticateActor(AppPermission.USER_CREATE, AppPermission.ROLE_ASSIGN);
        Role elevatedRole = role(AppPermission.PRODUCT_DELETE);
        when(roleRepository.findAllById(Set.of(7L))).thenReturn(List.of(elevatedRole));

        var request = new UserRequest();
        request.setUsername("new-user");
        request.setEmail("new-user@example.com");
        request.setRoleIds(Set.of(7L));
        var service = new UserService(
                appUserRepository,
                roleRepository,
                new AuthorizationService(appUserRepository),
                keycloakAdminClient,
                keycloakProperties);

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
        ArgumentCaptor<Specification<AppUser>> specifications = ArgumentCaptor.captor();
        verify(appUserRepository, times(3)).findAll(specifications.capture(), eq(pageable));
        assertThat(specifications.getAllValues())
                .extracting(specification -> predicateCount(specification))
                .containsExactly(2, 0, 0);

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
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(appUserRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(keycloakAdminClient.createUser(keycloakProperties, "user", "u@e.com", "password1"))
                .thenReturn(new KeycloakAdminClient.KeycloakUser("subject", "user", "u@e.com"));
        when(keycloakProperties.issuer()).thenReturn("https://issuer");
        UserRequest request = request(" user ", " U@E.COM ", 1L);
        request.setActive(false);

        AppUser result = service().create(request);

        assertThat(result.getUsername()).isEqualTo("user");
        assertThat(result.getEmail()).isEqualTo("u@e.com");
        assertThat(result.getOidcIssuer()).isEqualTo("https://issuer");
        assertThat(result.getOidcSubject()).isEqualTo("subject");
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "short"})
    void create_requiresPasswordBeforeProvisioningIdentity(String password) {
        Role role = UserDomainTest.role("R", true, AppPermission.PRODUCT_VIEW);
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        UserRequest request = request("user", "u@e.com", 1L);
        request.setPassword(password);

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(UserException.class)
                .hasMessage("Password must be at least 8 characters.");
        verify(keycloakAdminClient, never()).createUser(any(), any(), any(), any());
    }

    @Test
    void create_reportsKeycloakFailureWithoutPersistingLocalUser() {
        Role role = UserDomainTest.role("R", true, AppPermission.PRODUCT_VIEW);
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(keycloakAdminClient.createUser(keycloakProperties, "user", "u@e.com", "password1"))
                .thenThrow(new IllegalStateException("remote failure"));
        UserRequest request = request("user", "u@e.com", 1L);

        assertThatThrownBy(() -> service().create(request))
                .isInstanceOf(UserException.class)
                .hasMessage("The Keycloak account could not be created.");
        verify(appUserRepository, never()).save(any());
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
    void update_enforcesOptimisticLockImmutableIdentityAndUpdatesRoles() throws Exception {
        AppUser user = new AppUser("old", "old@e.com", "issuer", "subject");
        set(user, "version", 2L);
        Role role = UserDomainTest.role("R", true, AppPermission.PRODUCT_VIEW);
        UserRequest request = request(" old ", " OLD@E.COM ", 1L);
        request.setVersion(2L);
        when(appUserRepository.findWithRolesById(7L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(1L))).thenReturn(List.of(role));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(appUserRepository.save(user)).thenReturn(user);

        assertThat(service().update(7L, request).getUsername()).isEqualTo("old");
        verify(authorizationService).check(AppPermission.ROLE_ASSIGN);
        verify(authorizationService).invalidateUser(7L);

        request.setVersion(3L);

        assertThatThrownBy(() -> service().update(7L, request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("another administrator");
        request.setVersion(2L);
        request.setUsername("new");

        assertThatThrownBy(() -> service().update(7L, request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("managed by Keycloak");
        request.setUsername("old");
        request.setEmail("new@e.com");

        assertThatThrownBy(() -> service().update(7L, request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("managed by Keycloak");
        when(appUserRepository.findWithRolesById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(8L, request))
                .isInstanceOf(UserException.class)
                .hasMessage("User was not found.");
    }

    @Test
    void update_validatesOnlyRolesAddedOrRemovedFromAMixedSelection() throws Exception {
        Role retained = UserDomainTest.role("RETAINED", true, AppPermission.PRODUCT_VIEW);
        Role removed = UserDomainTest.role("REMOVED", true, AppPermission.PRODUCT_VIEW);
        Role added = UserDomainTest.role("ADDED", true, AppPermission.PRODUCT_VIEW);
        set(retained, "id", 1L);
        set(removed, "id", 2L);
        set(added, "id", 3L);
        AppUser user = new AppUser("old", "old@e.com", null, null);
        user.addRole(retained);
        user.addRole(removed);
        set(user, "version", 2L);
        UserRequest request = request("old", "old@e.com", 1L, 3L);
        request.setVersion(2L);
        when(appUserRepository.findWithRolesById(7L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(1L, 3L))).thenReturn(List.of(retained, added));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(appUserRepository.save(user)).thenReturn(user);

        AppUser updated = service().update(7L, request);

        assertThat(updated.getRoles()).containsExactlyInAnyOrder(retained, added);
        verify(authorizationService).invalidateUser(7L);
    }

    @Test
    void update_rejectsRemovingARoleAboveTheActorPriority() throws Exception {
        Role high = UserDomainTest.role("HIGH", true, AppPermission.PRODUCT_VIEW);
        high.update(high.getName(), high.getDescription(), 80, true, high.getPermissions());
        set(high, "id", 1L);
        Role low = UserDomainTest.role("LOW", true, AppPermission.PRODUCT_VIEW);
        set(low, "id", 2L);
        AppUser user = new AppUser("old", "old@e.com", null, null);
        user.addRole(high);
        set(user, "version", 2L);
        UserRequest request = request("new", "new@e.com", 2L);
        request.setVersion(2L);
        when(appUserRepository.findWithRolesById(7L)).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(2L))).thenReturn(List.of(low));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);

        assertThatThrownBy(() -> service().update(7L, request))
                .isInstanceOf(UserException.class)
                .hasMessageContaining("above your priority");
    }

    @Test
    void update_withoutRoleChanges_doesNotRequireRoleAssignment() throws Exception {
        Role role = UserDomainTest.role("R", true, AppPermission.PRODUCT_VIEW);
        set(role, "id", 1L);
        AppUser user = new AppUser("old", "old@e.com", null, null);
        user.addRole(role);
        set(user, "version", 2L);
        UserRequest request = request("old", "old@e.com", 1L);
        request.setVersion(2L);
        when(appUserRepository.findWithRolesById(7L)).thenReturn(Optional.of(user));
        when(appUserRepository.save(user)).thenReturn(user);

        assertThat(service().update(7L, request).getUsername()).isEqualTo("old");
        verify(authorizationService).check(AppPermission.USER_UPDATE);
        verify(authorizationService, never()).check(AppPermission.ROLE_ASSIGN);
        verify(authorizationService).invalidateUser(7L);
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

        verify(authorizationService, times(3)).invalidateUser(1L);
        when(appUserRepository.findWithRolesById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deactivate(2L)).isInstanceOf(UserException.class);
    }

    @Test
    void capabilityQueriesDelegateToAuthorization() {
        var create = Set.of(AppPermission.USER_CREATE, AppPermission.ROLE_ASSIGN);
        when(authorizationService.canAll(create)).thenReturn(true);
        when(authorizationService.can(AppPermission.USER_UPDATE)).thenReturn(false);
        when(authorizationService.can(AppPermission.ROLE_ASSIGN)).thenReturn(true);
        when(authorizationService.can(AppPermission.USER_DELETE)).thenReturn(true);

        assertThat(service().canCreateUsers()).isTrue();
        assertThat(service().canUpdateUsers()).isFalse();
        assertThat(service().canAssignRoles()).isTrue();
        assertThat(service().canDeleteUsers()).isTrue();
        verify(authorizationService).canAll(create);
        verify(authorizationService).can(AppPermission.USER_UPDATE);
        verify(authorizationService).can(AppPermission.ROLE_ASSIGN);
    }

    private UserService service() {
        return new UserService(
                appUserRepository, roleRepository, authorizationService, keycloakAdminClient, keycloakProperties);
    }

    private UserRequest request(String username, String email, Long... ids) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword("password1");
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
        Role role = new Role("TEST", "Test", null);
        role.update(role.getName(), role.getDescription(), 100, true, Set.of(permissions));

        return role;
    }
}
