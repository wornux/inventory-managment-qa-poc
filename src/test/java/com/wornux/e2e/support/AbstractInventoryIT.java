package com.wornux.e2e.support;

import com.vaadin.testbench.BrowserTestBase;
import com.vaadin.testbench.Parameters;
import com.vaadin.testbench.annotations.BrowserConfiguration;
import com.vaadin.testbench.annotations.RunLocally;
import com.vaadin.testbench.parallel.Browser;
import com.wornux.e2e.page.ApplicationShell;
import com.wornux.e2e.page.LoginPage;
import com.wornux.security.authorization.AuthorizationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("e2e")
@RunLocally(Browser.CHROME)
@Import(E2eContainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.locations=classpath:db/migration/prod",
            "vaadin.launch-browser=false",
            "management.tracing.export.enabled=false"
        })
public abstract class AbstractInventoryIT extends BrowserTestBase {

    protected static final String SYSTEM_ADMINISTRATOR = "SYSTEM_ADMINISTRATOR";
    protected static final String INVENTORY_MANAGER = "INVENTORY_MANAGER";
    protected static final String WAREHOUSE_OPERATOR = "WAREHOUSE_OPERATOR";
    protected static final String INVENTORY_VIEWER = "INVENTORY_VIEWER";
    protected static final String REPORT_VIEWER = "E2E_REPORT_VIEWER";

    @LocalServerPort
    private int serverPort;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthorizationService authorizationService;

    private E2eFixtures fixtures;

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.security.oauth2.client.registration.keycloak.client-secret",
                () -> E2eEnvironment.CLIENT_SECRET);
        properties.add(
                "spring.security.oauth2.client.provider.keycloak.authorization-uri",
                () -> keycloak("/protocol/openid-connect/auth"));
        properties.add(
                "spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> keycloak("/protocol/openid-connect/token"));
        properties.add(
                "spring.security.oauth2.client.provider.keycloak.jwk-set-uri",
                () -> keycloak("/protocol/openid-connect/certs"));
        properties.add(
                "spring.security.oauth2.client.provider.keycloak.user-info-uri",
                () -> keycloak("/protocol/openid-connect/userinfo"));
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> keycloak(""));
        properties.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> keycloak("/protocol/openid-connect/certs"));
        properties.add("app.keycloak.end-session-uri", () -> keycloak("/protocol/openid-connect/logout"));
        properties.add("app.keycloak.admin-bootstrap.enabled", () -> true);
        properties.add("app.keycloak.admin-bootstrap.server-url", E2eEnvironment::keycloakUrl);
        properties.add("app.keycloak.admin-bootstrap.admin-username", () -> "admin");
        properties.add("app.keycloak.admin-bootstrap.admin-password", () -> E2eEnvironment.ADMIN_PASSWORD);
        properties.add("app.keycloak.admin-bootstrap.user-username", () -> E2eEnvironment.ADMIN_USERNAME);
        properties.add("app.keycloak.admin-bootstrap.user-email", () -> E2eEnvironment.ADMIN_USERNAME);
        properties.add("app.keycloak.admin-bootstrap.user-password", () -> E2eEnvironment.ADMIN_PASSWORD);
    }

    @BrowserConfiguration
    public List<DesiredCapabilities> browserConfiguration() {
        var options = new ChromeOptions();
        options.addArguments("--headless=new", "--disable-dev-shm-usage", "--no-sandbox", "--window-size=1440,900");

        return List.of(new DesiredCapabilities(options));
    }

    @BeforeAll
    void configureEnvironment() {
        fixtures = new E2eFixtures(jdbc, authorizationService);
        KeycloakClientConfigurer.allowApplicationCallback(applicationUrl());
        Parameters.setScreenshotErrorDirectory("target/e2e/screenshots/failures");
        Parameters.setScreenshotReferenceDirectory("src/test/screenshots/reference");
    }

    @BeforeEach
    void resetData() {
        fixtures.resetScenarioData();
        fixtures.giveAdministratorRole(SYSTEM_ADMINISTRATOR);
    }

    protected ApplicationShell signInAs(String roleCode) {
        fixtures.giveAdministratorRole(roleCode);
        getDriver().get(applicationUrl() + "/login");
        new LoginPage(this).signIn(E2eEnvironment.ADMIN_USERNAME, E2eEnvironment.ADMIN_PASSWORD);

        return new ApplicationShell(this).waitUntilLoaded();
    }

    protected void open(String route) {
        getDriver().get(applicationUrl() + route);
    }

    protected void setViewport(int width, int height) {
        ((HasCdp) getDriver())
                .executeCdpCommand(
                        "Emulation.setDeviceMetricsOverride",
                        Map.of("width", width, "height", height, "deviceScaleFactor", 1, "mobile", false));
    }

    protected void givenProduct(String sku, String name, int quantity, int minimumStock) {
        fixtures.createProduct(sku, name, quantity, minimumStock);
    }

    protected void givenMovement(String sku, String movementType, int quantityDelta) {
        fixtures.createMovement(sku, movementType, quantityDelta);
    }

    protected void givenNumberedProducts() {
        for (int index = 0; index < 55; index++) {
            givenProduct("E2E-PAGE-%03d".formatted(index), "Paged Product %03d".formatted(index), 20, 5);
        }
    }

    protected String applicationUrl() {
        return "http://localhost:" + serverPort;
    }

    private static String keycloak(String suffix) {
        return E2eEnvironment.keycloakUrl() + "/realms/wornux" + suffix;
    }
}
