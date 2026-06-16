package com.wornux.security;

import com.wornux.user.AppUserService;
import com.wornux.user.OidcUserProfile;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(KeycloakAdminBootstrapProperties.class)
public class KeycloakAdminBootstrap implements ApplicationRunner {

    private final KeycloakAdminBootstrapProperties properties;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AppUserService appUserService;

    public KeycloakAdminBootstrap(
            KeycloakAdminBootstrapProperties properties,
            KeycloakAdminClient keycloakAdminClient,
            AppUserService appUserService) {
        this.properties = properties;
        this.keycloakAdminClient = keycloakAdminClient;
        this.appUserService = appUserService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        try {
            properties.validate();
            String token = keycloakAdminClient.adminToken(properties);
            KeycloakAdminClient.KeycloakUser keycloakUser = keycloakAdminClient.ensureUser(properties, token);
            appUserService.provisionSystemAdministrator(new OidcUserProfile(
                    properties.issuer(),
                    keycloakUser.id(),
                    keycloakUser.username(),
                    keycloakUser.email()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Keycloak admin bootstrap failed.", exception);
        }
    }
}
