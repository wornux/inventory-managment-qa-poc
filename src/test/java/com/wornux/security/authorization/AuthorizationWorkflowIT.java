package com.wornux.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wornux.api.stockmovement.StockMovementRequestDto;
import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryRepository;
import com.wornux.catalog.MovementType;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductRepository;
import com.wornux.catalog.StockMovement;
import com.wornux.catalog.StockMovementRepository;
import com.wornux.security.AppJwtAuthenticationConverter;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import com.wornux.user.RoleRepository;
import com.wornux.user.UserRequest;
import com.wornux.user.UserService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.flyway.enabled=true",
                "spring.flyway.locations=classpath:db/migration/prod"
        })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
class AuthorizationWorkflowIT {

    private static final String REALM = "wornux-test";
    private static final String TEST_CLIENT = "integration-tests";
    private static final String SYSTEM_ADMINISTRATOR = "SYSTEM_ADMINISTRATOR";
    private static final String WAREHOUSE_OPERATOR = "WAREHOUSE_OPERATOR";
    private static final String INVENTORY_VIEWER = "INVENTORY_VIEWER";
    private static final String ADMINISTRATOR_USERNAME = "system-administrator";
    private static final String ADMINISTRATOR_EMAIL = "system-administrator@example.test";
    private static final String WAREHOUSE_OPERATOR_USERNAME = "warehouse-operator";
    private static final String INVENTORY_USER_USERNAME = "inventory-user";
    private static final String DEACTIVATION_OPERATOR_USERNAME = "deactivation-operator";
    private static final String KEYCLOAK_ADMIN_USERNAME = "keycloak-admin";
    private static final String KEYCLOAK_ADMIN_PASSWORD = randomSecret();
    private static final String ADMINISTRATOR_PASSWORD = randomSecret();
    private static final String WAREHOUSE_OPERATOR_PASSWORD = randomSecret();
    private static final String INVENTORY_USER_PASSWORD = randomSecret();
    private static final String DEACTIVATION_OPERATOR_PASSWORD = randomSecret();
    private static final ParameterizedTypeReference<Map<String, Object>> TOKEN_RESPONSE_TYPE =
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
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("keycloak/wornux-test-realm.json"),
                    "/opt/keycloak/data/import/wornux-test-realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                    .forStatusCode(200))
            .withStartupTimeout(Duration.ofMinutes(2));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    AppJwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    UserService userService;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    StockMovementRepository stockMovementRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", AuthorizationWorkflowIT::issuerUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",() -> issuerUri() + "/protocol/openid-connect/certs");
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", AuthorizationWorkflowIT::issuerUri);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-id", () -> TEST_CLIENT);
        registry.add("spring.security.oauth2.client.registration.keycloak.client-authentication-method", () -> "none");
        registry.add("app.keycloak.admin-bootstrap.enabled", () -> "true");
        registry.add("app.keycloak.admin-bootstrap.server-url", AuthorizationWorkflowIT::keycloakBaseUrl);
        registry.add("app.keycloak.admin-bootstrap.realm", () -> REALM);
        registry.add("app.keycloak.admin-bootstrap.admin-realm", () -> "master");
        registry.add("app.keycloak.admin-bootstrap.admin-client-id", () -> "admin-cli");
        registry.add("app.keycloak.admin-bootstrap.admin-username", () -> KEYCLOAK_ADMIN_USERNAME);
        registry.add("app.keycloak.admin-bootstrap.admin-password", () -> KEYCLOAK_ADMIN_PASSWORD);
        registry.add("app.keycloak.admin-bootstrap.user-username", () -> ADMINISTRATOR_USERNAME);
        registry.add("app.keycloak.admin-bootstrap.user-email", () -> ADMINISTRATOR_EMAIL);
        registry.add("app.keycloak.admin-bootstrap.user-password", () -> ADMINISTRATOR_PASSWORD);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorAssignsWarehouseRoleToKeycloakUser_userCanRecordStockMovement() throws Exception {
        String warehouseToken = accessToken(WAREHOUSE_OPERATOR_USERNAME, WAREHOUSE_OPERATOR_PASSWORD);
        provisionThroughApi(warehouseToken);
        AppUser warehouseUser = requiredUser(WAREHOUSE_OPERATOR_USERNAME);
        Product product = persistProduct("ASSIGNMENT", 5);

        assertThat(warehouseUser.getOidcIssuer()).isEqualTo(issuerUri());
        assertThat(warehouseUser.getOidcSubject()).isNotBlank();
        assertThat(warehouseUser.getRoles()).extracting(Role::getCode).containsExactly(INVENTORY_VIEWER);

        authenticateWithAdministratorToken();
        replaceRole(warehouseUser.getId(), WAREHOUSE_OPERATOR);

        recordPurchase(warehouseToken, product.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(WAREHOUSE_OPERATOR_USERNAME))
                .andExpect(jsonPath("$.data.quantityDelta").value(3));

        Product persistedProduct = productRepository.findById(product.getId()).orElseThrow();
        StockMovement persistedMovement = movementForProduct(product.getId());
        AppUser persistedUser =
                appUserRepository.findWithRolesById(warehouseUser.getId()).orElseThrow();

        assertThat(persistedUser.getRoles()).extracting(Role::getCode).containsExactly(WAREHOUSE_OPERATOR);
        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(8);
        assertThat(persistedMovement.getUser().getId()).isEqualTo(warehouseUser.getId());
        assertThat(persistedMovement.getMovementType()).isEqualTo(MovementType.PURCHASE);
        assertThat(persistedMovement.getQuantityDelta()).isEqualTo(3);
    }

    @Test
    void administratorPromotesKeycloakViewer_sameTokenGainsStockMovementPermissionImmediately() throws Exception {
        String viewerToken = accessToken(INVENTORY_USER_USERNAME, INVENTORY_USER_PASSWORD);
        provisionThroughApi(viewerToken);
        AppUser viewer = requiredUser(INVENTORY_USER_USERNAME);
        Product product = persistProduct("PROMOTION", 5);

        recordPurchase(viewerToken, product.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(stockMovementRepository.existsByProductId(product.getId())).isFalse();
        assertThat(viewer.getRoles()).extracting(Role::getCode).containsExactly(INVENTORY_VIEWER);

        authenticateWithAdministratorToken();
        replaceRole(viewer.getId(), WAREHOUSE_OPERATOR);

        recordPurchase(viewerToken, product.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(INVENTORY_USER_USERNAME));

        Product persistedProduct = productRepository.findById(product.getId()).orElseThrow();
        StockMovement persistedMovement = movementForProduct(product.getId());

        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(8);
        assertThat(persistedMovement.getUser().getId()).isEqualTo(viewer.getId());
    }

    @Test
    void administratorDeactivatesKeycloakOperator_validTokenCanNoLongerRecordMovements() throws Exception {
        String operatorToken = accessToken(DEACTIVATION_OPERATOR_USERNAME, DEACTIVATION_OPERATOR_PASSWORD);
        provisionThroughApi(operatorToken);
        AppUser operator = requiredUser(DEACTIVATION_OPERATOR_USERNAME);
        Product product = persistProduct("DEACTIVATION", 5);

        authenticateWithAdministratorToken();
        replaceRole(operator.getId(), WAREHOUSE_OPERATOR);

        recordPurchase(operatorToken, product.getId()).andExpect(status().isCreated());
        Long firstMovementId = movementForProduct(product.getId()).getId();

        authenticateWithAdministratorToken();
        userService.deactivate(operator.getId());

        recordPurchase(operatorToken, product.getId())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication failed."));

        AppUser persistedOperator =
                appUserRepository.findWithRolesById(operator.getId()).orElseThrow();
        Product persistedProduct = productRepository.findById(product.getId()).orElseThrow();

        assertThat(persistedOperator.isActive()).isFalse();
        assertThat(persistedOperator.getRoles()).extracting(Role::getCode).containsExactly(WAREHOUSE_OPERATOR);
        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(8);
        assertThat(stockMovementRepository.findAll())
                .filteredOn(movement -> movement.getProduct().getId().equals(product.getId()))
                .extracting(StockMovement::getId)
                .containsExactly(firstMovementId);
    }


    private void provisionThroughApi(String accessToken) throws Exception {
        mockMvc.perform(get("/api/me/permissions").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    private void authenticateWithAdministratorToken() {
        Jwt jwt = jwtDecoder.decode(accessToken(ADMINISTRATOR_USERNAME, ADMINISTRATOR_PASSWORD));
        SecurityContextHolder.getContext().setAuthentication(jwtAuthenticationConverter.convert(jwt));

        AppUser administrator = requiredUser(ADMINISTRATOR_USERNAME);
        assertThat(administrator.getOidcIssuer()).isEqualTo(issuerUri());
        assertThat(administrator.getRoles()).extracting(Role::getCode).contains(SYSTEM_ADMINISTRATOR);
    }

    private void replaceRole(Long userId, String roleCode) {
        AppUser user = appUserRepository.findWithRolesById(userId).orElseThrow();
        Role role = requiredRole(roleCode);
        UserRequest request = userRequest(user.getUsername(), user.getEmail(), role.getId());
        request.setActive(user.isActive());
        request.setVersion(user.getVersion());

        userService.update(userId, request);
    }

    private ResultActions recordPurchase(String accessToken, Long productId) throws Exception {
        var request = new StockMovementRequestDto(productId, MovementType.PURCHASE, 3, "Supplier restock");

        return mockMvc.perform(post("/api/stock-movements")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsBytes(request)));
    }

    private Product persistProduct(String fixture, int quantityOnHand) {
        return transactionTemplate.execute(status -> {
            Category category = categoryRepository.save(
                    new Category(fixture + " Power Tools", "Electric tools used in authorization tests"));

            return productRepository.save(new Product(
                    "TOOL-" + fixture + "-001",
                    fixture + " Cordless Drill",
                    "18V cordless drill",
                    new BigDecimal("25.00"),
                    quantityOnHand,
                    2,
                    category,
                    null,
                    true));
        });
    }

    private StockMovement movementForProduct(Long productId) {
        return stockMovementRepository.findAll().stream()
                .filter(movement -> movement.getProduct().getId().equals(productId))
                .findFirst()
                .orElseThrow();
    }

    private AppUser requiredUser(String username) {
        return appUserRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
                .orElseThrow();
    }

    private Role requiredRole(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    private static UserRequest userRequest(String username, String email, Long roleId) {
        UserRequest request = new UserRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setRoleIds(Set.of(roleId));

        return request;
    }

    private static String accessToken(String username, String password) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", TEST_CLIENT);
        form.add("grant_type", "password");
        form.add("scope", "openid profile email");
        form.add("username", username);
        form.add("password", password);

        Map<String, Object> response = RestClient.create()
                .post()
                .uri(keycloakBaseUrl() + "/realms/" + REALM + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TOKEN_RESPONSE_TYPE);
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

    private static String keycloakBaseUrl() {
        return "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);
    }

    private static String randomSecret() {
        return UUID.randomUUID().toString();
    }
}
