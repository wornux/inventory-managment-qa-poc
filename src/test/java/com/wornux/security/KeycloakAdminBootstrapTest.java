package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.user.AppUserService;
import com.wornux.user.OidcUserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminBootstrapTest {
    @Mock
    KeycloakAdminClient client;

    @Mock
    AppUserService users;

    @Test
    void disabledBootstrapDoesNothing() {
        new KeycloakAdminBootstrap(properties(false), client, users).run(null);

        verify(client, never()).adminToken(any());
    }

    @Test
    void provisionsTheEnsuredKeycloakUserAsSystemAdministrator() {
        var properties = properties(true);
        when(client.adminToken(properties)).thenReturn("token");
        when(client.ensureUser(properties, "token"))
                .thenReturn(new KeycloakAdminClient.KeycloakUser("id", "sysadmin", "sys@example.com"));

        new KeycloakAdminBootstrap(properties, client, users).run(null);

        verify(users)
                .provisionSystemAdministrator(
                        new OidcUserProfile("https://keycloak/realms/app", "id", "sysadmin", "sys@example.com"));
    }

    @Test
    void wrapsConfigurationAndRemoteFailuresWithBootstrapContext() {
        var invalid =
                new KeycloakAdminBootstrapProperties(true, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> new KeycloakAdminBootstrap(invalid, client, users).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Keycloak admin bootstrap failed.")
                .hasRootCauseMessage("Keycloak admin bootstrap property is required: server-url");
    }

    static KeycloakAdminBootstrapProperties properties(boolean enabled) {
        return new KeycloakAdminBootstrapProperties(
                enabled,
                "http://keycloak:7777",
                "https://keycloak/realms/app",
                "app",
                "master",
                "admin-cli",
                "admin",
                "secret",
                "sysadmin",
                "sys@example.com",
                "user-secret");
    }
}
