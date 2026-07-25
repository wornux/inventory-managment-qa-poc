package com.wornux.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String OAUTH2_SCHEME = "oauth2";

    @Bean
    OpenAPI openAPI(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        return new OpenAPI()
                .info(new Info()
                        .title("QA Final Project API")
                        .version("1.0")
                        .description("REST API for authentication and product inventory operations."))
                .components(new Components()
                        .addSecuritySchemes(
                                OAUTH2_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .flows(new OAuthFlows()
                                                .authorizationCode(new OAuthFlow()
                                                        .authorizationUrl(issuerUri + "/protocol/openid-connect/auth")
                                                        .tokenUrl(issuerUri + "/protocol/openid-connect/token")
                                                        .scopes(new Scopes()
                                                                .addString("openid", "Authenticate with Keycloak")
                                                                .addString("profile", "Read the user profile")
                                                                .addString("email", "Read the user email"))))));
    }
}
