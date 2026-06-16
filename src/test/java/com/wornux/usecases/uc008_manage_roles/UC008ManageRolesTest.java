package com.wornux.usecases.uc008_manage_roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.catalog.StockMovementRepository;
import com.wornux.ui.views.RolesView;
import com.wornux.usecases.PostgresContainerConfig;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Permission;
import com.wornux.user.PermissionRepository;
import com.wornux.user.Role;
import com.wornux.user.RoleException;
import com.wornux.user.RoleFilter;
import com.wornux.user.RoleRepository;
import com.wornux.user.RoleRequest;
import com.wornux.user.RoleService;
import com.wornux.user.UserException;
import com.wornux.user.UserRequest;
import com.wornux.user.UserService;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC008ManageRolesTest {

    private final RoleService roleService;
    private final UserService userService;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AppUserRepository appUserRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PasswordEncoder passwordEncoder;

    private Permission readProduct;
    private Permission readUser;

    @Autowired
    UC008ManageRolesTest(
            RoleService roleService,
            UserService userService,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            AppUserRepository appUserRepository,
            StockMovementRepository stockMovementRepository,
            PasswordEncoder passwordEncoder) {
        this.roleService = roleService;
        this.userService = userService;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.appUserRepository = appUserRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void cleanUsers() {
        stockMovementRepository.deleteAll();
        appUserRepository.deleteAll();
        List<Permission> permissions = permissionRepository.findAssignablePermissions();
        readProduct = permissions.stream()
                .filter(permission -> permission.getCode().equals("PRODUCT:READ"))
                .findFirst()
                .orElseGet(() -> permissions.getFirst());
        readUser = permissions.stream()
                .filter(permission -> permission.getCode().equals("USER:READ"))
                .findFirst()
                .orElseGet(() -> permissions.getLast());
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void mainFlow_viewSearchCreateEditAndDeactivateRoles() {
        Role created = roleService.create(request(uniqueCode(), "Cycle Counter", "Counts inventory", true, readProduct));

        assertThat(created.isSystemRole()).isFalse();
        assertThat(roleService.search(new RoleFilter("cycle", false, true)))
                .extracting(Role::getId)
                .containsExactly(created.getId());

        RoleRequest update = request(created.getCode(), "Cycle Counter Lead", "Leads counts", true, readProduct, readUser);
        update.setVersion(created.getVersion());
        roleService.update(created.getId(), update);

        Role updated = roleService.get(created.getId());
        assertThat(updated.getName()).isEqualTo("Cycle Counter Lead");
        assertThat(updated.getPermissions()).extracting(Permission::getCode).contains(readProduct.getCode(), readUser.getCode());

        roleService.deactivate(updated.getId());

        assertThat(roleService.search(new RoleFilter(updated.getCode(), false, false)))
                .extracting(Role::getId)
                .containsExactly(updated.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af1_duplicateRoleCodeIsRejected() {
        Role role = roleService.create(request(uniqueCode(), "Duplicate", null, true, readProduct));

        assertThatThrownBy(() -> roleService.create(request(role.getCode().toLowerCase(), "Duplicate 2", null, true, readProduct)))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role code already exists. Please choose a different one.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af2_missingRequiredFieldsAreRejected() {
        assertThatThrownBy(() -> roleService.create(request("", "", null, true, readProduct)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Role code is required.")
                .hasMessageContaining("Role name is required.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af3_attemptToEditSystemRoleIsRejected() {
        Role systemRole = roleRepository.findByCode("SYSTEM_ADMINISTRATOR").orElseThrow();
        RoleRequest update = request(systemRole.getCode(), "Admin Changed", null, true, readProduct);
        update.setVersion(systemRole.getVersion());

        assertThatThrownBy(() -> roleService.update(systemRole.getId(), update))
                .isInstanceOf(RoleException.class)
                .hasMessage("System roles cannot be edited.");

        assertThatThrownBy(() -> roleService.deactivate(systemRole.getId()))
                .isInstanceOf(RoleException.class)
                .hasMessage("System roles cannot be edited.");
    }

    @Test
    @WithMockUser(username = "viewer", roles = "INVENTORY_VIEWER")
    void af4_insufficientPermissionsCannotManageRoles() {
        assertThat(roleService.canManageRoles()).isFalse();

        assertThatThrownBy(() -> roleService.search(new RoleFilter("", null, true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ROLE:READ permission is required.");
        assertThatThrownBy(() -> roleService.create(request(uniqueCode(), "Blocked", null, true, readProduct)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ROLE:CREATE/UPDATE/DELETE/ASSIGN permission is required.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af5_roleWithUsersCanBeDeactivatedWithoutRemovingAssignments() {
        Role role = roleService.create(request(uniqueCode(), "Assigned Role", null, true, readProduct));
        AppUser user = createUser("assigned-" + UUID.randomUUID().toString().substring(0, 8), role);

        assertThat(roleService.userCount(role.getId())).isEqualTo(1);

        roleService.deactivate(role.getId());

        AppUser reloaded = appUserRepository.findWithRolesById(user.getId()).orElseThrow();
        assertThat(reloaded.getRoles()).extracting(Role::getId).contains(role.getId());
        assertThat(roleService.get(role.getId()).isActive()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af6_noPermissionsSelectedIsRejected() {
        RoleRequest request = request(uniqueCode(), "No Permission Role", null, true, readProduct);
        request.setPermissionIds(new LinkedHashSet<>());

        assertThatThrownBy(() -> roleService.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessage("At least one permission must be selected.");
    }

    @Test
    void af7_sidebarFormDirtyStateIsOwnedByRolesView() throws NoSuchFieldException {
        assertThat(RolesView.class.getDeclaredField("dirty")).isNotNull();
        assertThat(RolesView.class.getDeclaredField("dirtyDialog")).isNotNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af8_concurrentEditConflictIsRejected() {
        Role role = roleService.create(request(uniqueCode(), "Conflict Role", null, true, readProduct));
        RoleRequest stale = request(role.getCode(), "Conflict Updated", null, true, readProduct);
        stale.setVersion(role.getVersion() + 1);

        assertThatThrownBy(() -> roleService.update(role.getId(), stale))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role was updated by another administrator. Refresh the form and try again.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af9_permissionNoLongerActiveIsRejected() {
        Role role = roleService.create(request(uniqueCode(), "Inactive Permission Role", null, true, readProduct));
        readProduct.deactivate();
        permissionRepository.saveAndFlush(readProduct);

        RoleRequest update = request(role.getCode(), "Inactive Permission Role", null, true, readProduct);
        update.setVersion(role.getVersion());

        assertThatThrownBy(() -> roleService.update(role.getId(), update))
                .isInstanceOf(RoleException.class)
                .hasMessage("One or more permissions are no longer active and have been deselected.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br01_roleCodeIsUniqueAndImmutable() {
        Role role = roleService.create(request(uniqueCode(), "Immutable Role", null, true, readProduct));
        RoleRequest update = request(role.getCode() + "_CHANGED", "Immutable Role", null, true, readProduct);
        update.setVersion(role.getVersion());

        assertThatThrownBy(() -> roleService.update(role.getId(), update))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role code cannot be changed.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br02AndBr03_roleNameIsRequiredAndDescriptionIsOptional() {
        af2_missingRequiredFieldsAreRejected();

        Role role = roleService.create(request(uniqueCode(), "No Description", null, true, readProduct));

        assertThat(role.getDescription()).isNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br04AndBr05_systemRolesAreProtectedAndCustomRolesDeactivate() {
        af3_attemptToEditSystemRoleIsRejected();

        Role role = roleService.create(request(uniqueCode(), "Custom Role", null, true, readProduct));
        roleService.deactivate(role.getId());

        assertThat(roleService.get(role.getId()).isActive()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br06_roleMustHaveAtLeastOnePermissionAssigned() {
        af6_noPermissionsSelectedIsRejected();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br07AndBr08_deactivatedRolesRetainExistingAssignmentsButCannotBeNewlyAssigned() {
        Role role = roleService.create(request(uniqueCode(), "Retained Role", null, true, readProduct));
        createUser("retained-" + UUID.randomUUID().toString().substring(0, 8), role);
        roleService.deactivate(role.getId());

        UserRequest request = userRequest("blocked-" + UUID.randomUUID().toString().substring(0, 8), role);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(UserException.class)
                .hasMessage("At least one role must be selected.");
        assertThat(roleService.userCount(role.getId())).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br09_searchFiltersByRoleCodeAndNameCaseInsensitivePartialMatch() {
        Role role = roleService.create(request(uniqueCode(), "Needle Role", null, true, readProduct));

        assertThat(roleService.search(new RoleFilter("needle", false, true)))
                .extracting(Role::getId)
                .containsExactly(role.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br10_gridCountsUsersAndPermissions() {
        Role role = roleService.create(request(uniqueCode(), "Counted Role", null, true, readProduct, readUser));
        createUser("counted-" + UUID.randomUUID().toString().substring(0, 8), role);

        assertThat(roleService.userCount(role.getId())).isEqualTo(1);
        assertThat(roleService.permissionCount(role.getId())).isEqualTo(2);
    }

    private RoleRequest request(String code, String name, String description, boolean active, Permission... permissions) {
        var request = new RoleRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription(description);
        request.setActive(active);
        request.setPermissionIds(java.util.Arrays.stream(permissions)
                .map(Permission::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        return request;
    }

    private AppUser createUser(String username, Role role) {
        AppUser user = new AppUser(username, username + "@example.com", passwordEncoder.encode("password123"));
        user.addRole(role);
        return appUserRepository.saveAndFlush(user);
    }

    private UserRequest userRequest(String username, Role role) {
        var request = new UserRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setActive(true);
        request.setRoleIds(new LinkedHashSet<>(java.util.List.of(role.getId())));
        return request;
    }

    private String uniqueCode() {
        return "UC8_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
