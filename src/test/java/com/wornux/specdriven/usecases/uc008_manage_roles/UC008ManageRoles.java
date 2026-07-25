package com.wornux.specdriven.usecases.uc008_manage_roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.AppUserService;
import com.wornux.user.Role;
import com.wornux.user.RoleException;
import com.wornux.user.RoleRepository;
import com.wornux.user.RoleRequest;
import com.wornux.user.RoleService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
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
class UC008ManageRoles {

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
    void mainFlow_createCustomRoleWithTypedPermissions() {
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
    void mainFlow_activeRolePermissionsBecomeAuthorities() {
        Role role = new Role("CATALOG_EDITOR", "Catalog Editor", null, false);
        role.update(role.getName(), role.getDescription(), true, Set.of(AppPermission.PRODUCT_UPDATE));
        AppUser user = new AppUser("editor", "editor@example.com", "issuer", "subject");
        user.addRole(role);

        var authorities = new AppUserService(appUserRepository, roleRepository).authorities(user);

        assertThat(authorities).extracting(authority -> authority.getAuthority())
                .containsExactly("product:update");
    }

    @Test
    void br14_deactivatedRoleStopsAuthorizingExistingSessions() {
        Role role = new Role("VIEWER", "Viewer", null);
        role.update(role.getName(), role.getDescription(), true, Set.of(AppPermission.PRODUCT_VIEW));
        AppUser user = new AppUser("viewer", "viewer@example.com", "issuer", "subject");
        user.addRole(role);
        when(appUserRepository.findForAuthorization("viewer")).thenReturn(java.util.Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("viewer", "password", java.util.List.of()));
        var authorizationService = new AuthorizationService(appUserRepository);

        assertThat(authorizationService.can(AppPermission.PRODUCT_VIEW)).isTrue();
        role.deactivate();
        assertThat(authorizationService.can(AppPermission.PRODUCT_VIEW)).isFalse();
    }

    @Test
    void af4_missingRoleCreatePermissionIsDenied() {
        authenticate("role:view", "role:assign", "product:update");

        assertThatThrownBy(() -> roleService.create(request(AppPermission.PRODUCT_UPDATE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Missing permission role:create");
        verifyNoInteractions(roleRepository);
    }

    @Test
    void af9_actorCannotAssignPermissionTheyDoNotHave() {
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
