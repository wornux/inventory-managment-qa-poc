package com.wornux.api.it.support;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

final class ApiTestEnvironment {

    static final String REALM = "wornux-test";
    static final String CLIENT = "integration-tests";
    static final String AUTOMATION_CLIENT = "inventory-automation";
    static final String AUTOMATION_CLIENT_SECRET = randomSecret();
    static final String MANAGER_USERNAME = "system-administrator";
    static final String MANAGER_EMAIL = "system-administrator@example.test";
    static final String VIEWER_USERNAME = "inventory-user";
    static final String KEYCLOAK_ADMIN_USERNAME = "keycloak-admin";
    static final String KEYCLOAK_ADMIN_PASSWORD = randomSecret();
    static final String MANAGER_PASSWORD = randomSecret();
    static final String VIEWER_PASSWORD = randomSecret();
    private static final String WAREHOUSE_PASSWORD = randomSecret();
    private static final String DEACTIVATION_PASSWORD = randomSecret();

    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.1")
            .withDatabaseName("stocks")
            .withUsername("postgres")
            .withPassword("postgres");

    @SuppressWarnings("resource")
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.6")
            .withExposedPorts(8080)
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", KEYCLOAK_ADMIN_USERNAME)
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", KEYCLOAK_ADMIN_PASSWORD)
            .withEnv("TEST_AUTOMATION_CLIENT_SECRET", AUTOMATION_CLIENT_SECRET)
            .withEnv("TEST_WAREHOUSE_OPERATOR_PASSWORD", WAREHOUSE_PASSWORD)
            .withEnv("TEST_INVENTORY_USER_PASSWORD", VIEWER_PASSWORD)
            .withEnv("TEST_DEACTIVATION_OPERATOR_PASSWORD", DEACTIVATION_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/wornux-test-realm.json"),
                    "/opt/keycloak/data/import/wornux-test-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        Startables.deepStart(Stream.of(POSTGRES, KEYCLOAK)).join();
    }

    private ApiTestEnvironment() {}

    static String keycloakBaseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    static String issuerUri() {
        return keycloakBaseUrl() + "/realms/" + REALM;
    }

    private static String randomSecret() {
        return UUID.randomUUID().toString();
    }
}
