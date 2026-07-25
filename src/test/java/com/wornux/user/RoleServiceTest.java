package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(
                roleRepository,
                appUserRepository,
                new AuthorizationService(appUserRepository));
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
        assertThat(role.getPermissions()).containsExactly(
                AppPermission.PRODUCT_VIEW,
                AppPermission.PRODUCT_UPDATE);
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
                .flatMap(java.util.Optional::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Role role = new Role("ADMIN", "Admin", null);
        role.update(role.getName(), role.getDescription(), true, permissions);
        AppUser user = new AppUser("admin", "admin@example.com", "issuer", "subject");
        user.addRole(role);
        when(appUserRepository.findForAuthorization("admin")).thenReturn(java.util.Optional.of(user));

        var authorities = Arrays.stream(permissionCodes).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "password", authorities));
    }
}
