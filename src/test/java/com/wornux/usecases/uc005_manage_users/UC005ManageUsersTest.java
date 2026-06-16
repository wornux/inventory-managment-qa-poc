package com.wornux.usecases.uc005_manage_users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.catalog.StockMovementRepository;
import com.wornux.ui.views.UsersView;
import com.wornux.usecases.PostgresContainerConfig;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import com.wornux.user.RoleRepository;
import com.wornux.user.UserException;
import com.wornux.user.UserFilter;
import com.wornux.user.UserRequest;
import com.wornux.user.UserService;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashSet;
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
class UC005ManageUsersTest {

    private final UserService userService;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PasswordEncoder passwordEncoder;

    private Role viewerRole;
    private Role managerRole;
    private Role adminRole;

    @Autowired
    UC005ManageUsersTest(
            UserService userService,
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            StockMovementRepository stockMovementRepository,
            PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void cleanUsers() {
        stockMovementRepository.deleteAll();
        appUserRepository.deleteAll();
        viewerRole = roleRepository.findByCode("INVENTORY_VIEWER").orElseThrow();
        managerRole = roleRepository.findByCode("INVENTORY_MANAGER").orElseThrow();
        adminRole = roleRepository.findByCode("SYSTEM_ADMINISTRATOR").orElseThrow();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void mainFlow_viewSearchCreateEditAndDeactivateUsers() {
        createAdmin("admin");

        AppUser created = userService.create(request(
                "managed",
                "managed@example.com",
                "password123",
                "password123",
                true,
                viewerRole));

        assertThat(passwordEncoder.matches("password123", created.getPasswordHash())).isTrue();
        assertThat(userService.search(new UserFilter("manage", true)))
                .extracting(AppUser::getUsername)
                .containsExactly("managed");

        UserRequest update = request("managed2", "managed2@example.com", "", "", true, managerRole);
        update.setVersion(created.getVersion());
        userService.update(created.getId(), update);

        AppUser updated = userService.get(created.getId());
        assertThat(updated.getEmail()).isEqualTo("managed2@example.com");
        assertThat(updated.getRoles()).extracting(Role::getCode).containsExactly("INVENTORY_MANAGER");

        userService.deactivate(updated.getId());

        assertThat(userService.search(new UserFilter("", false)))
                .extracting(AppUser::getUsername)
                .containsExactly("managed2");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af1_duplicateUsernameIsRejected() {
        createAdmin("admin");
        userService.create(request("dupe", "one@example.com", "password123", "password123", true, viewerRole));

        assertThatThrownBy(() -> userService.create(
                request("DUPE", "two@example.com", "password123", "password123", true, viewerRole)))
                .isInstanceOf(UserException.class)
                .hasMessage("Username already exists. Please choose a different one.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af2_duplicateEmailIsRejected() {
        createAdmin("admin");
        userService.create(request("one", "same@example.com", "password123", "password123", true, viewerRole));

        assertThatThrownBy(() -> userService.create(
                request("two", "SAME@example.com", "password123", "password123", true, viewerRole)))
                .isInstanceOf(UserException.class)
                .hasMessage("Email already registered. Please use a different one.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af3_invalidEmailFormatIsRejected() {
        createAdmin("admin");

        assertThatThrownBy(() -> userService.create(
                request("bademail", "not-an-email", "password123", "password123", true, viewerRole)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Invalid email address.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af4_missingRequiredFieldsAreRejected() {
        createAdmin("admin");

        assertThatThrownBy(() -> userService.create(request("", "", "", "", true, viewerRole)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Username is required.")
                .hasMessageContaining("Email is required.");

        assertThatThrownBy(() -> userService.create(request(
                "nopassword", "nopassword@example.com", "", "", true, viewerRole)))
                .isInstanceOf(UserException.class)
                .hasMessage("Password is required.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af5_weakPasswordIsRejected() {
        createAdmin("admin");

        assertThatThrownBy(() -> userService.create(
                request("weak", "weak@example.com", "short", "short", true, viewerRole)))
                .isInstanceOf(UserException.class)
                .hasMessage("Password must be at least 8 characters.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af6_passwordMismatchIsRejected() {
        createAdmin("admin");

        assertThatThrownBy(() -> userService.create(
                request("mismatch", "mismatch@example.com", "password123", "password124", true, viewerRole)))
                .isInstanceOf(UserException.class)
                .hasMessage("Passwords do not match.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af7_noRolesSelectedIsRejected() {
        createAdmin("admin");
        UserRequest request = request("noroles", "noroles@example.com", "password123", "password123", true, viewerRole);
        request.setRoleIds(new LinkedHashSet<>());

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(UserException.class)
                .hasMessage("At least one role must be selected.");
    }

    @Test
    @WithMockUser(username = "viewer", roles = "INVENTORY_VIEWER")
    void af8_insufficientPermissionsCannotManageUsers() {
        createUser("viewer", viewerRole);

        assertThat(userService.canManageUsers()).isFalse();
        assertThatThrownBy(() -> userService.search(new UserFilter("", true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("USER:READ permission is required.");
        assertThatThrownBy(() -> userService.create(
                request("blocked", "blocked@example.com", "password123", "password123", true, viewerRole)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("USER:CREATE/UPDATE/DELETE/ASSIGN permission is required.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af9_adminCannotDeactivateOwnAccount() {
        AppUser admin = createAdmin("admin");

        assertThatThrownBy(() -> userService.deactivate(admin.getId()))
                .isInstanceOf(UserException.class)
                .hasMessage("You cannot deactivate your own account.");
    }

    @Test
    void af10_sidebarFormDirtyStateIsOwnedByUsersView() throws NoSuchFieldException {
        assertThat(UsersView.class.getDeclaredField("dirty")).isNotNull();
        assertThat(UsersView.class.getDeclaredField("dirtyDialog")).isNotNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af11_concurrentEditConflictIsRejected() {
        createAdmin("admin");
        AppUser user = userService.create(request(
                "conflict", "conflict@example.com", "password123", "password123", true, viewerRole));
        UserRequest stale = request("conflict2", "conflict2@example.com", "", "", true, viewerRole);
        stale.setVersion(user.getVersion() + 1);

        assertThatThrownBy(() -> userService.update(user.getId(), stale))
                .isInstanceOf(UserException.class)
                .hasMessage("User was updated by another administrator. Refresh the form and try again.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br01ThroughBr10_userManagementRulesAreEnforced() {
        createAdmin("admin");
        AppUser user = userService.create(request(
                "rules", "rules@example.com", "password123", "password123", true, viewerRole));

        assertThat(user.getPasswordHash()).startsWith("$2").doesNotContain("password123");
        assertThat(user.getRoles()).isNotEmpty();
        assertThat(userService.search(new UserFilter("ULE", true))).extracting(AppUser::getUsername).containsExactly("rules");

        userService.deactivate(user.getId());

        assertThat(userService.search(new UserFilter("", false))).extracting(AppUser::getUsername).containsExactly("rules");
        assertThat(appUserRepository.findById(user.getId())).isPresent();
    }

    private AppUser createAdmin(String username) {
        return createUser(username, adminRole);
    }

    private AppUser createUser(String username, Role role) {
        AppUser user = new AppUser(username, username + "@example.com", passwordEncoder.encode("password123"));
        user.addRole(role);
        return appUserRepository.saveAndFlush(user);
    }

    private UserRequest request(
            String username,
            String email,
            String password,
            String confirmPassword,
            boolean active,
            Role role) {
        var request = new UserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        request.setActive(active);
        request.setRoleIds(new LinkedHashSet<>(java.util.List.of(role.getId())));
        return request;
    }
}
