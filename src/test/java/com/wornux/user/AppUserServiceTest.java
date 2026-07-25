package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.wornux.security.permission.AppPermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppUserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Test
    void authorities_includesPermissionsFromActiveRoles() {
        Role role = new Role("CATALOG_EDITOR", "Catalog Editor", null, false);
        role.update(role.getName(), role.getDescription(), true, Set.of(AppPermission.PRODUCT_UPDATE));
        AppUser user = new AppUser("editor", "editor@example.com", "issuer", "subject");
        user.addRole(role);

        var authorities = new AppUserService(appUserRepository, roleRepository).authorities(user);

        assertThat(authorities).extracting(authority -> authority.getAuthority())
                .containsExactly("product:update");
    }
}
