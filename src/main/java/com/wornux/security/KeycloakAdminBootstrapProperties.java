package com.wornux.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.keycloak.admin-bootstrap")
public record KeycloakAdminBootstrapProperties(
        boolean enabled,
        String serverUrl,
        String realm,
        String adminRealm,
        String adminClientId,
        String adminUsername,
        String adminPassword,
        String userUsername,
        String userEmail,
        String userPassword) {

    String issuer() {
        return trimTrailingSlash(serverUrl) + "/realms/" + required("realm", realm);
    }

    String baseUrl() {
        return trimTrailingSlash(required("server-url", serverUrl));
    }

    void validate() {
        required("server-url", serverUrl);
        required("realm", realm);
        required("admin-realm", adminRealm);
        required("admin-client-id", adminClientId);
        required("admin-username", adminUsername);
        required("admin-password", adminPassword);
        required("user-username", userUsername);
        required("user-email", userEmail);
        required("user-password", userPassword);
    }

    private static String required(String name, String value) {
        String normalized = value == null ? "" : value.trim();

        if (normalized.isBlank()) {
            throw new IllegalStateException("Keycloak admin bootstrap property is required: " + name);
        }

        return normalized;
    }

    private static String trimTrailingSlash(String value) {
        String normalized = required("server-url", value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
