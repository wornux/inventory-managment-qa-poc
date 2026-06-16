package com.wornux.usecases.uc011_rest_api_support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryRepository;
import com.wornux.catalog.ProductRepository;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierRepository;
import com.wornux.usecases.PostgresContainerConfig;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import com.wornux.user.RoleRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC011RestApiSupportTest {

    private static final String PASSWORD = "password123";

    private MockMvc mockMvc;
    private final WebApplicationContext context;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    private Category category;
    private Supplier supplier;
    private AppUser manager;
    private AppUser viewer;

    @Autowired
    UC011RestApiSupportTest(
            WebApplicationContext context,
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            PasswordEncoder passwordEncoder) {
        this.context = context;
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        category = categoryRepository.findByNameIgnoreCase("General").orElseThrow();
        supplier = supplierRepository.findByActiveTrueOrderByNameAsc().getFirst();
        manager = userWithRole("api-manager", "INVENTORY_MANAGER");
        viewer = userWithRole("api-viewer", "INVENTORY_VIEWER");
    }

    @Test
    void mainFlow_loginAndProductCrudUseJwtAndStandardResponses() throws Exception {
        String token = login(manager.getUsername());
        String sku = uniqueSku();

        MvcResult create = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "API Product", null, null)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.startsWith("/api/products/")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sku").value(sku))
                .andExpect(jsonPath("$.data.category.id").value(category.getId()))
                .andReturn();

        Integer id = JsonPath.read(create.getResponse().getContentAsString(), "$.data.id");
        Integer version = JsonPath.read(create.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .queryParam("text", sku)
                        .queryParam("page", "0")
                        .queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].sku").value(sku))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(5));

        mockMvc.perform(get("/api/products/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));

        mockMvc.perform(put("/api/products/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "API Product Updated", version.longValue(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("API Product Updated"));

        mockMvc.perform(delete("/api/products/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(productRepository.findById(id.longValue())).isEmpty();
    }

    @Test
    void af1_openSignupEndpointDoesNotRequireJwt() throws Exception {
        String username = "signup-" + suffix();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s@example.com",
                                  "password": "%s",
                                  "confirmPassword": "%s"
                                }
                                """.formatted(username, username, PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(username));
    }

    @Test
    void af2_missingOrInvalidJwtReturnsStandardUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication failed."));

        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer broken.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].message").value("A valid bearer token is required."));
    }

    @Test
    void af3_insufficientPermissionReturnsForbiddenWrapper() throws Exception {
        String token = login(viewer.getUsername());

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(uniqueSku(), "Forbidden Product", null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied."));
    }

    @Test
    void af4_validationFailureReturnsFieldErrors() throws Exception {
        String token = login(manager.getUsername());

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sku": "",
                                  "name": "",
                                  "unitPrice": -1,
                                  "quantityOnHand": -1,
                                  "minimumStock": -1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    @Test
    void af5_productNotFoundReturnsNotFoundWrapper() throws Exception {
        String token = login(manager.getUsername());

        mockMvc.perform(get("/api/products/{id}", 999_999)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Product was not found."));
    }

    @Test
    void af6_duplicateSkuReturnsConflictWrapper() throws Exception {
        String token = login(manager.getUsername());
        String sku = uniqueSku();

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "Original Product", null, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, "Duplicate Product", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("SKU already exists. Please choose a different one."));
    }

    @Test
    void br01_apiRoutesDoNotBreakVaadinRoutes() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }

    @Test
    void br10_throughBr14_productApiUsesRequestResponseDtosPageableAndAdvice() throws Exception {
        String token = login(manager.getUsername());

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .queryParam("page", "0")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.page.size").value(1));

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").exists());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "usernameOrEmail": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private AppUser userWithRole(String prefix, String roleCode) {
        Role role = roleRepository.findByCode(roleCode).orElseThrow();
        AppUser user = new AppUser(
                prefix + "-" + suffix(),
                prefix + "-" + suffix() + "@example.com",
                passwordEncoder.encode(PASSWORD));
        user.addRole(role);
        return appUserRepository.saveAndFlush(user);
    }

    private String productJson(String sku, String name, Long version, Long supplierId) {
        return """
                {
                  "sku": "%s",
                  "name": "%s",
                  "description": "Created through API",
                  "unitPrice": 12.50,
                  "quantityOnHand": 4,
                  "minimumStock": 1,
                  "categoryId": %d,
                  "supplierId": %s,
                  "active": true,
                  "version": %s
                }
                """.formatted(
                sku,
                name,
                category.getId(),
                supplierId == null ? supplier.getId().toString() : supplierId.toString(),
                version == null ? "null" : version.toString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniqueSku() {
        return "API-" + suffix().toUpperCase();
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
