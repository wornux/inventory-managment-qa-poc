package com.wornux.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.wornux.security.permission.AppAction;
import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import com.wornux.user.RoleRepository;
import com.wornux.user.UserRequest;
import com.wornux.user.UserService;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
                "spring.flyway.locations=classpath:db/migration/prod",
                "management.otlp.metrics.export.enabled=false"
        })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
class AuthorizationWorkflowIT {

    private static final String REALM = "wornux-test";
    private static final String TEST_CLIENT = "integration-tests";
    private static final String SYSTEM_ADMINISTRATOR = "SYSTEM_ADMINISTRATOR";
    private static final String INVENTORY_MANAGER = "INVENTORY_MANAGER";
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

    @MockitoSpyBean
    AuthorizationService authorizationService;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpointPermissions")
    void endpoint_permissionMatrixEnforcesAuthenticationAndRequiredPermission(EndpointPermissionCase endpoint)
            throws Exception {
        String username = persistPermissionUser(null);
        Product product = persistProduct("PERMISSION-" + UUID.randomUUID(), 5);
        long productCount = productRepository.count();
        String originalName = product.getName();

        performEndpoint(endpoint, product, null).andExpect(status().isUnauthorized());

        doThrow(new AccessDeniedException("Missing permission " + endpoint.permission().code()))
                .when(authorizationService)
                .check(endpoint.permission());

        performEndpoint(endpoint, product, username).andExpect(status().isForbidden());
        verify(authorizationService).check(endpoint.permission());
        assertOperationDidNotMutateState(endpoint, product, productCount, originalName);

        clearInvocations(authorizationService);
        doNothing().when(authorizationService).check(endpoint.permission());

        performEndpoint(endpoint, product, username).andExpect(status().is(endpoint.successStatus()));
        verify(authorizationService).check(endpoint.permission());
        assertSuccessfulOperationState(endpoint, product, productCount);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("systemRoles")
    void systemRole_enforcesEndpointPermissionMatrix(String roleCode) throws Exception {
        Role role = requiredRole(roleCode);
        String username = persistPermissionUser(role);

        for (EndpointPermissionCase endpoint : endpointPermissions().toList()) {
            String fixture = roleCode.substring(0, Math.min(roleCode.length(), 8))
                    + "-"
                    + endpoint.permission().action()
                    + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
            Product product = persistProduct(fixture, 5);
            boolean allowed = role.getPermissions().stream()
                    .anyMatch(granted -> granted.grants(endpoint.permission()));
            int expectedStatus = allowed ? endpoint.successStatus() : 403;

            performEndpoint(endpoint, product, username)
                    .andExpect(status().is(expectedStatus));
        }
    }

    @Test
    void permissionsEndpoint_requiresAuthenticationButNoDomainPermission() throws Exception {
        String username = persistPermissionUser(null);

        mockMvc.perform(get("/api/me/permissions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/permissions").with(jwt().jwt(token -> token.subject(username))))
                .andExpect(status().isOk());
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


    static Stream<EndpointPermissionCase> endpointPermissions() {
        return Stream.of(
                new EndpointPermissionCase(
                        "GET /api/products",
                        AppPermission.PRODUCT_VIEW,
                        200,
                        (product, mapper) -> get("/api/products")),
                new EndpointPermissionCase(
                        "GET /api/products/{id}",
                        AppPermission.PRODUCT_VIEW,
                        200,
                        (product, mapper) -> get("/api/products/{id}", product.getId())),
                new EndpointPermissionCase(
                        "POST /api/products",
                        AppPermission.PRODUCT_CREATE,
                        201,
                        (product, mapper) -> post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(productRequest(
                                        permissionFixture(AppPermission.PRODUCT_CREATE, product),
                                        product.getCategory().getId(),
                                        null)))),
                new EndpointPermissionCase(
                        "PUT /api/products/{id}",
                        AppPermission.PRODUCT_UPDATE,
                        200,
                        (product, mapper) -> put("/api/products/{id}", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(productRequest(
                                        permissionFixture(AppPermission.PRODUCT_UPDATE, product),
                                        product.getCategory().getId(),
                                        product.getVersion())))),
                new EndpointPermissionCase(
                        "DELETE /api/products/{id}",
                        AppPermission.PRODUCT_DELETE,
                        200,
                        (product, mapper) -> delete("/api/products/{id}", product.getId())),
                new EndpointPermissionCase(
                        "GET /api/categories",
                        AppPermission.CATEGORY_VIEW,
                        200,
                        (product, mapper) -> get("/api/categories")),
                new EndpointPermissionCase(
                        "GET /api/suppliers",
                        AppPermission.SUPPLIER_VIEW,
                        200,
                        (product, mapper) -> get("/api/suppliers")),
                new EndpointPermissionCase(
                        "GET /api/stock-movements",
                        AppPermission.STOCK_MOVEMENT_VIEW,
                        200,
                        (product, mapper) -> get("/api/stock-movements")),
                new EndpointPermissionCase(
                        "POST /api/stock-movements",
                        AppPermission.STOCK_MOVEMENT_CREATE,
                        201,
                        (product, mapper) -> post("/api/stock-movements")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(new StockMovementRequestDto(
                                        product.getId(),
                                        MovementType.PURCHASE,
                                        3,
                                        "Permission matrix restock")))));
    }

    static Stream<String> systemRoles() {
        return Stream.of(SYSTEM_ADMINISTRATOR, INVENTORY_MANAGER, WAREHOUSE_OPERATOR, INVENTORY_VIEWER);
    }

    private ResultActions performEndpoint(EndpointPermissionCase endpoint, Product product, String username)
            throws Exception {
        MockHttpServletRequestBuilder request = endpoint.request().build(product, jsonMapper);

        if (username != null) {
            request.with(jwt().jwt(token -> token.subject(username)));
        }

        return mockMvc.perform(request);
    }

    private String persistPermissionUser(Role role) {
        String suffix = UUID.randomUUID().toString();
        String username = "permission-" + suffix;

        return transactionTemplate.execute(status -> {
            AppUser user = new AppUser(username, username + "@example.test", null, null);
            if (role != null) {
                user.addRole(roleRepository.findById(role.getId()).orElseThrow());
            }
            appUserRepository.save(user);

            return username;
        });
    }

    private void assertOperationDidNotMutateState(
            EndpointPermissionCase endpoint, Product product, long productCount, String originalName) {
        AppAction action = endpoint.permission().action();

        switch (action) {
            case CREATE -> {
                if (endpoint.permission() == AppPermission.PRODUCT_CREATE) {
                    assertThat(productRepository.count()).isEqualTo(productCount);
                } else if (endpoint.permission() == AppPermission.STOCK_MOVEMENT_CREATE) {
                    assertThat(productRepository.findById(product.getId()).orElseThrow().getQuantityOnHand())
                            .isEqualTo(5);
                    assertThat(stockMovementRepository.existsByProductId(product.getId())).isFalse();
                }
            }
            case UPDATE -> assertThat(productRepository.findById(product.getId()).orElseThrow().getName())
                    .isEqualTo(originalName);
            case DELETE -> assertThat(productRepository.existsById(product.getId())).isTrue();
            default -> {
            }
        }
    }

    private void assertSuccessfulOperationState(
            EndpointPermissionCase endpoint, Product product, long productCount) {
        AppAction action = endpoint.permission().action();

        switch (action) {
            case CREATE -> {
                if (endpoint.permission() == AppPermission.PRODUCT_CREATE) {
                    assertThat(productRepository.count()).isEqualTo(productCount + 1);
                } else if (endpoint.permission() == AppPermission.STOCK_MOVEMENT_CREATE) {
                    assertThat(productRepository.findById(product.getId()).orElseThrow().getQuantityOnHand())
                            .isEqualTo(8);
                    assertThat(stockMovementRepository.existsByProductId(product.getId())).isTrue();
                }
            }
            case UPDATE -> assertThat(productRepository.findById(product.getId()).orElseThrow().getName())
                    .isEqualTo(permissionFixture(endpoint.permission(), product) + " Product");
            case DELETE -> assertThat(productRepository.existsById(product.getId())).isFalse();
            default -> {
            }
        }
    }

    private static String permissionFixture(AppPermission permission, Product product) {
        return permission.name() + "-" + product.getId();
    }

    private static Map<String, Object> productRequest(String fixture, Long categoryId, Long version) {
        var request = new HashMap<String, Object>();
        request.put("sku", "AUTH-" + fixture);
        request.put("name", fixture + " Product");
        request.put("description", "Authorization endpoint matrix fixture");
        request.put("unitPrice", new BigDecimal("25.00"));
        request.put("quantityOnHand", 5);
        request.put("minimumStock", 2);
        request.put("categoryId", categoryId);
        request.put("active", true);
        if (version != null) {
            request.put("version", version);
        }

        return request;
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

    @FunctionalInterface
    interface EndpointRequest {
        MockHttpServletRequestBuilder build(Product product, JsonMapper mapper) throws Exception;
    }

    record EndpointPermissionCase(
            String endpoint, AppPermission permission, int successStatus, EndpointRequest request) {

        @Override
        public String toString() {
            return endpoint + " requires " + permission.code();
        }
    }
}
