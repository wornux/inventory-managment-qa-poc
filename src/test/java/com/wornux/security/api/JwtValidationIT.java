package com.wornux.security.api;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration/prod",
                "management.otlp.metrics.export.enabled=false",
                "vaadin.launch-browser=false"
        })
class JwtValidationIT {

    private static final String REALM = "wornux-test";
    private static final String WRONG_ISSUER_REALM = "wrong-issuer-test";
    private static final String TEST_CLIENT = "integration-tests";
    private static final String INVENTORY_USER_USERNAME = "inventory-user";
    private static final String WRONG_ISSUER_CLIENT = "wrong-issuer-tests";
    private static final String WRONG_ISSUER_USERNAME = "wrong-issuer-user";
    private static final String KEYCLOAK_ADMIN_USERNAME = "keycloak-admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = randomSecret();
    private static final String WAREHOUSE_OPERATOR_PASSWORD = randomSecret();
    private static final String INVENTORY_USER_PASSWORD = randomSecret();
    private static final String DEACTIVATION_OPERATOR_PASSWORD = randomSecret();
    private static final String WRONG_ISSUER_PASSWORD = randomSecret();
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {
            };

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> keycloak = new GenericContainer<>("quay.io/keycloak/keycloak:26.6")
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", KEYCLOAK_ADMIN_USERNAME)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", KEYCLOAK_ADMIN_PASSWORD)
            .withEnv("TEST_WAREHOUSE_OPERATOR_PASSWORD", WAREHOUSE_OPERATOR_PASSWORD)
            .withEnv("TEST_INVENTORY_USER_PASSWORD", INVENTORY_USER_PASSWORD)
            .withEnv("TEST_DEACTIVATION_OPERATOR_PASSWORD", DEACTIVATION_OPERATOR_PASSWORD)
            .withEnv("TEST_WRONG_ISSUER_PASSWORD", WRONG_ISSUER_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/wornux-test-realm.json"),
                    "/opt/keycloak/data/import/wornux-test-realm.json")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/wrong-issuer-test-realm.json"),
                    "/opt/keycloak/data/import/wrong-issuer-test-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2));

    @LocalServerPort
    int serverPort;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", JwtValidationIT::issuerUri);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> issuerUri() + "/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", JwtValidationIT::issuerUri);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> TEST_CLIENT);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-authentication-method", () -> "none");
        registry.add("app.keycloak.admin-bootstrap.enabled", () -> "false");
    }

    @Test
    void validJwt_canAccessProtectedEndpoint() {
        apiRequest(accessToken(issuerUri(), TEST_CLIENT, INVENTORY_USER_USERNAME, INVENTORY_USER_PASSWORD))
                .get("/api/products")
                .then()
                .statusCode(200);
    }

    @Test
    void missingJwt_returns401() {
        apiRequest(null).get("/api/products").then().statusCode(401);
    }

    @Test
    void malformedJwt_returns401() {
        apiRequest("not-a-jwt").get("/api/products").then().statusCode(401);
    }

    @Test
    void tamperedJwt_returns401() {
        String validToken = accessToken(issuerUri(), TEST_CLIENT, INVENTORY_USER_USERNAME, INVENTORY_USER_PASSWORD);

        apiRequest(tamperSignature(validToken)).get("/api/products").then().statusCode(401);
    }

    @Test
    void expiredJwt_returns401() {
        String adminToken = accessToken(masterIssuerUri(), "admin-cli", KEYCLOAK_ADMIN_USERNAME, KEYCLOAK_ADMIN_PASSWORD);
        Map<String, Object> realm = realmRepresentation(adminToken);
        Object originalLifespan = realm.get("accessTokenLifespan");
        String expiredToken;

        try {
            realm.put("accessTokenLifespan", -120);
            updateRealm(adminToken, realm);
            expiredToken = accessToken(issuerUri(), TEST_CLIENT, INVENTORY_USER_USERNAME, INVENTORY_USER_PASSWORD);
        } finally {
            realm.put("accessTokenLifespan", originalLifespan);
            updateRealm(adminToken, realm);
        }

        apiRequest(expiredToken).get("/api/products").then().statusCode(401);
    }

    @Test
    void wrongIssuer_returns401() {
        String wrongIssuerToken =
                accessToken(wrongIssuerUri(), WRONG_ISSUER_CLIENT, WRONG_ISSUER_USERNAME, WRONG_ISSUER_PASSWORD);

        apiRequest(wrongIssuerToken).get("/api/products").then().statusCode(401);
    }

    private RequestSpecification apiRequest(String token) {
        RequestSpecification request = given()
                .baseUri("http://localhost:" + serverPort)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON);

        if (token != null) {
            request.auth().oauth2(token);
        }

        return request;
    }

    private static Map<String, Object> realmRepresentation(String adminToken) {
        Map<String, Object> realm = RestClient.create()
                .get()
                .uri(keycloakBaseUrl() + "/admin/realms/" + REALM)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .retrieve()
                .body(MAP_TYPE);

        if (realm == null) {
            throw new IllegalStateException("Keycloak did not return the test realm configuration.");
        }

        return realm;
    }

    private static void updateRealm(String adminToken, Map<String, Object> realm) {
        RestClient.create()
                .put()
                .uri(keycloakBaseUrl() + "/admin/realms/" + REALM)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(realm)
                .retrieve()
                .toBodilessEntity();
    }

    private static String tamperSignature(String token) {
        String[] segments = token.split("\\.");
        if (segments.length != 3 || segments[2].isEmpty()) {
            throw new IllegalArgumentException("Expected a compact signed JWT.");
        }

        char replacement = segments[2].charAt(0) == 'a' ? 'b' : 'a';
        segments[2] = replacement + segments[2].substring(1);

        return String.join(".", segments);
    }

    private static String accessToken(String tokenIssuer, String clientId, String username, String password) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        form.add("grant_type", "password");
        form.add("scope", "openid profile email");
        form.add("username", username);
        form.add("password", password);

        Map<String, Object> response = RestClient.create()
                .post()
                .uri(tokenIssuer + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MAP_TYPE);
        Object token = response == null ? null : response.get("access_token");

        if (token instanceof String value && !value.isBlank()) {
            return value;
        }

        throw new IllegalStateException("Keycloak token response did not include an access token.");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String issuerUri() {
        return keycloakBaseUrl() + "/realms/" + REALM;
    }

    private static String wrongIssuerUri() {
        return keycloakBaseUrl() + "/realms/" + WRONG_ISSUER_REALM;
    }

    private static String masterIssuerUri() {
        return keycloakBaseUrl() + "/realms/master";
    }

    private static String keycloakBaseUrl() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }

    private static String randomSecret() {
        return UUID.randomUUID().toString();
    }
}
