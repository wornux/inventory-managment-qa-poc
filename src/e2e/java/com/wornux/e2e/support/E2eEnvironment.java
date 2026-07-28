package com.wornux.e2e.support;

import java.nio.file.Path;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

final class E2eEnvironment {

    static final String ADMIN_USERNAME = "admin@wornux.com";
    static final String ADMIN_PASSWORD = "admin";
    static final String CLIENT_SECRET = "dGBqFp69vjM7kwUlXBXjjdVdJwlWbBWQ";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.1")
            .withDatabaseName("stocks")
            .withUsername("postgres")
            .withPassword("postgres");

    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(DockerImageName.parse("keycloak/keycloak:26.6"))
            .withCopyFileToContainer(
                    MountableFile.forHostPath(
                            Path.of("keycloak/import/wornux-realm.json").toAbsolutePath()),
                    "/opt/keycloak/data/import/wornux-realm.json")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD)
            .withEnv("KC_HTTP_ENABLED", "true")
            .withEnv("KC_HEALTH_ENABLED", "true")
            .withCommand("start-dev", "--import-realm", "--http-port=8080")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/wornux/.well-known/openid-configuration")
                    .forPort(8080)
                    .forStatusCode(200));

    static {
        KEYCLOAK.start();
    }

    private E2eEnvironment() {}

    static String keycloakUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }
}
