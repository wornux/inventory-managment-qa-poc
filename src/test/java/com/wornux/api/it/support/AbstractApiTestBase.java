package com.wornux.api.it.support;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractApiTestBase<T> {

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", ApiTestEnvironment.POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", ApiTestEnvironment.POSTGRES::getUsername);
        properties.add("spring.datasource.password", ApiTestEnvironment.POSTGRES::getPassword);
        properties.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> ApiTestEnvironment.CLIENT);
        properties.add("spring.security.oauth2.client.registration.keycloak.client-authentication-method", () -> "none");
        properties.add("spring.security.oauth2.client.provider.keycloak.authorization-uri", () -> keycloak("/protocol/openid-connect/auth"));
        properties.add("spring.security.oauth2.client.provider.keycloak.token-uri", () -> keycloak("/protocol/openid-connect/token"));
        properties.add("spring.security.oauth2.client.provider.keycloak.jwk-set-uri", () -> keycloak("/protocol/openid-connect/certs"));
        properties.add("spring.security.oauth2.client.provider.keycloak.user-info-uri", () -> keycloak("/protocol/openid-connect/userinfo"));
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", ApiTestEnvironment::issuerUri);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> keycloak("/protocol/openid-connect/certs"));
        properties.add("app.keycloak.end-session-uri", () -> keycloak("/protocol/openid-connect/logout"));
        properties.add("app.keycloak.admin-bootstrap.enabled", () -> true);
        properties.add("app.keycloak.admin-bootstrap.server-url", ApiTestEnvironment::keycloakBaseUrl);
        properties.add("app.keycloak.admin-bootstrap.realm", () -> ApiTestEnvironment.REALM);
        properties.add("app.keycloak.admin-bootstrap.admin-realm", () -> "master");
        properties.add("app.keycloak.admin-bootstrap.admin-client-id", () -> "admin-cli");
        properties.add("app.keycloak.admin-bootstrap.admin-username", () -> ApiTestEnvironment.KEYCLOAK_ADMIN_USERNAME);
        properties.add("app.keycloak.admin-bootstrap.admin-password", () -> ApiTestEnvironment.KEYCLOAK_ADMIN_PASSWORD);
        properties.add("app.keycloak.admin-bootstrap.user-username", () -> ApiTestEnvironment.MANAGER_USERNAME);
        properties.add("app.keycloak.admin-bootstrap.user-email", () -> ApiTestEnvironment.MANAGER_EMAIL);
        properties.add("app.keycloak.admin-bootstrap.user-password", () -> ApiTestEnvironment.MANAGER_PASSWORD);
    }

    @BeforeEach
    void configureCatalogFixtures() {
        jdbc.update("""
                insert into category (name, description)
                select 'API Test Category', 'Stable category for API scenarios.'
                where not exists (select 1 from category where name = 'API Test Category')
                """);
        jdbc.update("""
                insert into supplier (name, contact_name, email)
                select 'API Test Supplier', 'API Test Runner', 'api-tests@example.test'
                where not exists (select 1 from supplier where name = 'API Test Supplier')
                """);
    }

    protected abstract long firstCatalogId(String token, String path) throws Exception;

    protected abstract T createProduct(Map<String, Object> request) throws Exception;

    protected abstract T getProduct(String token, long productId) throws Exception;

    protected abstract void deleteProduct(long productId) throws Exception;

    private static String keycloak(String suffix) {
        return ApiTestEnvironment.issuerUri() + suffix;
    }
}
