package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class KeycloakAdminBootstrapPropertiesTest {
    private static final String[] VALUES = {"https://keycloak///", "app", "master", "admin-cli", "admin", "secret", "sysadmin", "sys@example.com", "user-secret"};

    @Test
    void normalizesUrlsAndValidatesCompleteConfiguration() {
        var properties = properties(VALUES);
        properties.validate();
        assertThat(properties.baseUrl()).isEqualTo("https://keycloak");
        assertThat(properties.issuer()).isEqualTo("https://keycloak/realms/app");
    }

    @Test
    void everyRequiredPropertyIsNamedOnFailure() {
        String[] names = {"server-url", "realm", "admin-realm", "admin-client-id", "admin-username", "admin-password", "user-username", "user-email", "user-password"};
        IntStream.range(0, names.length).forEach(index -> {
            String[] missing = VALUES.clone();
            missing[index] = index % 2 == 0 ? null : "  ";
            assertThatThrownBy(() -> properties(missing).validate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(names[index]);
        });
    }

    private static KeycloakAdminBootstrapProperties properties(String[] value) {
        return new KeycloakAdminBootstrapProperties(true, value[0], value[1], value[2], value[3], value[4], value[5], value[6], value[7], value[8]);
    }
}
