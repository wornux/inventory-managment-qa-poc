package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.security.permission.AppPermission;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserDomainTest {

    @Test
    void profile_normalizesClaimsAndRejectsEveryMissingRequiredClaim() {
        assertThat(new OidcUserProfile(" issuer ", " subject ", " user ", " USER@EXAMPLE.COM ").normalized())
                .isEqualTo(new OidcUserProfile("issuer", "subject", "user", "user@example.com"));

        for (var profile : new OidcUserProfile[] {
                new OidcUserProfile(null, "s", "u", "e"),
                new OidcUserProfile("i", " ", "u", "e"),
                new OidcUserProfile("i", "s", "", "e"),
                new OidcUserProfile("i", "s", "u", null)}) {
            assertThatThrownBy(profile::normalized)
                    .isInstanceOf(OidcProvisioningException.class)
                    .hasMessageStartingWith("Missing required OIDC claim:");
        }
    }

    @Test
    void entities_applyLifecycleAndDefensivelyExposePermissionValues() {
        Role first = role("FIRST", true, AppPermission.PRODUCT_VIEW);
        Role second = role("SECOND", true, AppPermission.PRODUCT_UPDATE);
        AppUser user = new AppUser("before", "before@example.com", "issuer", "subject");
        user.addRole(first);
        user.update("after", "after@example.com", false, Set.of(second));
        user.updateIdentity("oidc", "oidc@example.com", "new-issuer", "new-subject");

        assertThat(user.getUsername()).isEqualTo("oidc");
        assertThat(user.getEmail()).isEqualTo("oidc@example.com");
        assertThat(user.getOidcIssuer()).isEqualTo("new-issuer");
        assertThat(user.getOidcSubject()).isEqualTo("new-subject");
        assertThat(user.getRoles()).containsExactly(second);
        assertThat(user.getId()).isNull();
        assertThat(user.getVersion()).isNull();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
        user.setActive(true);
        user.deactivate();
        assertThat(user.isActive()).isFalse();

        Set<AppPermission> copy = second.getPermissions();
        copy.clear();
        assertThat(second.getPermissions()).containsExactly(AppPermission.PRODUCT_UPDATE);
        assertThat(second.getId()).isNull();
        assertThat(second.getVersion()).isNull();
        assertThat(second.getCreatedAt()).isNull();
        assertThat(second.getUpdatedAt()).isNull();
        assertThat(second.isSystemRole()).isFalse();
        second.deactivate();
        assertThat(second.isActive()).isFalse();
    }

    @Test
    void requests_defensivelyCopyNullableCollections() {
        var roleRequest = new RoleRequest();
        var permissions = new LinkedHashSet<>(Set.of(AppPermission.PRODUCT_VIEW));
        roleRequest.setPermissions(permissions);
        permissions.clear();
        assertThat(roleRequest.getPermissions()).containsExactly(AppPermission.PRODUCT_VIEW);
        roleRequest.setPermissions(null);
        assertThat(roleRequest.getPermissions()).isEmpty();

        var userRequest = new UserRequest();
        var ids = new LinkedHashSet<>(Set.of(1L));
        userRequest.setRoleIds(ids);
        ids.clear();
        assertThat(userRequest.getRoleIds()).containsExactly(1L);
        userRequest.setRoleIds(null);
        assertThat(userRequest.getRoleIds()).isEmpty();
    }

    @Test
    void provisioningException_preservesCause() {
        RuntimeException cause = new RuntimeException("cause");
        assertThat(new OidcProvisioningException("message", cause)).hasCause(cause);
        assertThat(new UserException("user")).hasMessage("user");
        assertThat(new RoleFilter("x", true, false).text()).isEqualTo("x");
        assertThat(new UserFilter("x", true).active()).isTrue();
    }

    @Test
    void jpaConstructors_initializeSafeDefaultsAndUnknownPermissionsAreIgnored() throws Exception {
        var userConstructor = AppUser.class.getDeclaredConstructor(); userConstructor.setAccessible(true);
        AppUser user = userConstructor.newInstance();
        assertThat(user.isActive()).isTrue(); assertThat(user.getRoles()).isEmpty();
        var roleConstructor = Role.class.getDeclaredConstructor(); roleConstructor.setAccessible(true);
        Role role = roleConstructor.newInstance();
        var field = Role.class.getDeclaredField("permissions"); field.setAccessible(true);
        field.set(role, new String[] {AppPermission.PRODUCT_VIEW.code(), "unknown"});
        assertThat(role.getPermissions()).containsExactly(AppPermission.PRODUCT_VIEW);
        assertThat(role.isActive()).isTrue(); assertThat(role.isSystemRole()).isTrue();
    }

    static Role role(String code, boolean active, AppPermission... permissions) {
        Role role = new Role(code, code, null, false);
        role.update(code, null, active, new LinkedHashSet<>(Set.of(permissions)));
        return role;
    }
}
