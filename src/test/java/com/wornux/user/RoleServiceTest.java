package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuthorizationService authorizationService;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, appUserRepository, new AuthorizationService(appUserRepository));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_persistsCustomRoleWithTypedPermissions() {
        authenticate("role:create", "role:assign", "product:update");
        RoleRequest request = request(AppPermission.PRODUCT_VIEW, AppPermission.PRODUCT_UPDATE);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role role = roleService.create(request);

        assertThat(role.getCode()).isEqualTo("CATALOG_EDITOR");
        assertThat(role.isSystemRole()).isFalse();
        assertThat(role.getPermissions()).containsExactly(AppPermission.PRODUCT_VIEW, AppPermission.PRODUCT_UPDATE);
    }

    @Test
    void members_returnsUsersAssignedToRole() {
        authenticate("role:view");
        AppUser member = new AppUser("editor", "editor@example.com", "issuer", "subject");
        when(appUserRepository.findDistinctByRolesIdOrderByUsernameAsc(7L)).thenReturn(List.of(member));

        assertThat(roleService.members(7L)).containsExactly(member);
    }

    @Test
    void userCounts_returnsCountsForRolesWithMembers() {
        authenticate("role:view");
        when(appUserRepository.countMembersByRoleIds(List.of(7L, 8L)))
                .thenReturn(List.<Object[]>of(new Object[] {7L, 3L}));

        assertThat(roleService.userCounts(List.of(7L, 8L)))
                .containsEntry(7L, 3L)
                .doesNotContainKey(8L);
    }

    @Test
    void create_withoutCreatePermission_throwsAccessDeniedException() {
        authenticate("role:view", "role:assign", "product:update");

        assertThatThrownBy(() -> roleService.create(request(AppPermission.PRODUCT_UPDATE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Missing permission role:create");
        verifyNoInteractions(roleRepository);
    }

    @Test
    void create_withUnassignablePermission_throwsRoleException() {
        authenticate("role:create", "role:assign", "product:view");

        assertThatThrownBy(() -> roleService.create(request(AppPermission.PRODUCT_DELETE)))
                .isInstanceOf(RoleException.class)
                .hasMessage("You cannot assign permissions that you do not have.");
    }

    @Test
    void reads_coverFiltersCountsMembersAndMissingRole() {
        RoleService service = directService();
        when(roleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThat(service.search(new RoleFilter(" QUERY ", true, false))).isEmpty();
        assertThat(service.search(null)).isEmpty();
        assertThat(service.search(new RoleFilter(null, null, null))).isEmpty();

        when(roleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role was not found.");
        when(appUserRepository.countByRolesId(1L)).thenReturn(3L);

        assertThat(service.userCount(1L)).isEqualTo(3L);
        assertThat(service.userCounts(List.of())).isEmpty();

        when(appUserRepository.findDistinctByRolesIdOrderByUsernameAsc(1L)).thenReturn(List.of());

        assertThat(service.members(1L)).isEmpty();
        assertThat(service.assignablePermissions()).containsExactly(AppPermission.values());
    }

    @Test
    void permissionCount_andUserCountsMapRepositoryValues() {
        Role role = UserDomainTest.role("R", true, AppPermission.PRODUCT_VIEW, AppPermission.PRODUCT_UPDATE);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        assertThat(directService().permissionCount(1L)).isEqualTo(2);

        when(appUserRepository.countMembersByRoleIds(Set.of(1L))).thenReturn(List.<Object[]>of(new Object[] {1L, 2L}));

        assertThat(directService().userCounts(Set.of(1L))).containsEntry(1L, 2L);
    }

    @Test
    void create_normalizesValuesAndRejectsDuplicateOrEmptyPermissions() {
        RoleService service = directService();
        when(authorizationService.canAll(any())).thenReturn(true);
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        RoleRequest request = request(AppPermission.PRODUCT_VIEW);
        request.setCode(" code ");
        request.setName(" Name ");
        request.setDescription("  ");
        request.setActive(false);

        Role role = service.create(request);

        assertThat(role.getCode()).isEqualTo("CODE");
        assertThat(role.getName()).isEqualTo("Name");
        assertThat(role.getDescription()).isNull();
        assertThat(role.isActive()).isFalse();

        request.setCode(null);
        request.setName(null);
        request.setDescription(" text ");

        assertThat(service.create(request).getDescription()).isEqualTo("text");

        when(roleRepository.existsByCodeIgnoreCase("")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("already exists");
        when(roleRepository.existsByCodeIgnoreCase("")).thenReturn(false);
        request.setPermissions(null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("one permission");
        request.setPermissions(Set.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("one permission");
        RoleRequest nullPermissions = mock(RoleRequest.class);
        when(nullPermissions.getPermissions()).thenReturn(null);

        assertThatThrownBy(() -> service.create(nullPermissions))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("one permission");
        request.setPermissions(Set.of(AppPermission.PRODUCT_VIEW));
        when(authorizationService.canAll(any())).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("cannot assign");
    }

    @Test
    void update_enforcesExistenceCustomRoleVersionAndImmutableCode() throws Exception {
        Role role = new Role("CODE", "Old", null, false);
        set(role, "version", 2L);
        RoleRequest request = request(AppPermission.PRODUCT_VIEW);
        request.setCode(" code ");
        request.setName(" New ");
        request.setVersion(2L);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(authorizationService.canAll(any())).thenReturn(true);
        when(roleRepository.save(role)).thenReturn(role);

        assertThat(directService().update(1L, request).getName()).isEqualTo("New");

        request.setVersion(3L);

        assertThatThrownBy(() -> directService().update(1L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("another administrator");
        request.setVersion(2L);
        request.setCode("OTHER");

        assertThatThrownBy(() -> directService().update(1L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("cannot be changed");
        when(roleRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directService().update(2L, request))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role was not found.");
        Role system = new Role("SYS", "System", null);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(system));

        assertThatThrownBy(() -> directService().update(3L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("System roles");
    }

    @Test
    void deactivate_handlesLifecycleMissingAndSystemRoles() {
        Role custom = new Role("C", "Custom", null, false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(custom));

        directService().deactivate(1L);

        assertThat(custom.isActive()).isFalse();
        verify(roleRepository).save(custom);

        when(roleRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directService().deactivate(2L)).isInstanceOf(RoleException.class);

        when(roleRepository.findById(3L)).thenReturn(Optional.of(new Role("S", "System", null)));

        assertThatThrownBy(() -> directService().deactivate(3L))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("System roles");
    }

    @Test
    void capabilitiesDelegateToAuthorization() {
        var create = Set.of(AppPermission.ROLE_CREATE, AppPermission.ROLE_ASSIGN);
        var update = Set.of(AppPermission.ROLE_UPDATE, AppPermission.ROLE_ASSIGN);
        when(authorizationService.canAll(create)).thenReturn(true);
        when(authorizationService.canAll(update)).thenReturn(false);
        when(authorizationService.can(AppPermission.ROLE_DELETE)).thenReturn(true);

        assertThat(directService().canCreateRoles()).isTrue();
        assertThat(directService().canUpdateRoles()).isFalse();
        assertThat(directService().canDeleteRoles()).isTrue();
        verify(authorizationService).canAll(create);
        verify(authorizationService).canAll(update);
    }

    private RoleService directService() {
        return new RoleService(roleRepository, appUserRepository, authorizationService);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);

        declared.set(target, value);
    }

    private RoleRequest request(AppPermission... permissions) {
        var request = new RoleRequest();
        request.setCode("catalog_editor");
        request.setName("Catalog Editor");
        request.setPermissions(new LinkedHashSet<>(Arrays.asList(permissions)));

        return request;
    }

    private void authenticate(String... permissionCodes) {
        var permissions = Arrays.stream(permissionCodes)
                .map(AppPermission::fromCode)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Role role = new Role("ADMIN", "Admin", null);
        role.update(role.getName(), role.getDescription(), true, permissions);
        AppUser user = new AppUser("admin", "admin@example.com", "issuer", "subject");
        user.addRole(role);
        when(appUserRepository.findForAuthorization("admin")).thenReturn(Optional.of(user));

        var authorities =
                Arrays.stream(permissionCodes).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("admin", "password", authorities));
    }
}
