package com.wornux.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void can_afterRoleDeactivation_revokesPermissionImmediately() {
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
}
