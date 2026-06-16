package com.wornux.usecases.uc012_openapi_documentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.wornux.usecases.PostgresContainerConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC012OpenApiDocumentationTest {

    private MockMvc mockMvc;
    private final WebApplicationContext context;
    private final Environment environment;

    @Autowired
    UC012OpenApiDocumentationTest(WebApplicationContext context, Environment environment) {
        this.context = context;
        this.environment = environment;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void mainFlow_swaggerUiLoadsOpenApiWithCurrentRestEndpointsAndJwtScheme() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("/swagger-ui/index.html")));

        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Map<String, Object>> productSecurity = JsonPath.read(body, "$.paths['/api/products'].get.security");
        assertThat(productSecurity).anySatisfy(requirement -> assertThat(requirement).containsKey("bearerAuth"));
    }

    @Test
    void af1_openApiJsonCanBeRequestedDirectlyWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.info.title").value("QA Final Project API"));
    }

    @Test
    void af2_swaggerStaticAssetsAreReachableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/swagger-ui.css"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void af3_protectedProductApiStillRejectsMissingJwtWithStandardResponse() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication failed."))
                .andExpect(jsonPath("$.errors[0].message").value("A valid bearer token is required."));
    }

    @Test
    void af4_openApiDependencyAndConfigurationExposeSwaggerRoutes() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk());
    }

    @Test
    void af5_andBr02_yamlConfigurationPreservesExistingApplicationSettings() {
        Path resourceRoot = Path.of("src/main/resources");

        assertThat(resourceRoot.resolve("application.yml")).exists();
        assertThat(resourceRoot.resolve("application.properties")).doesNotExist();
        assertThat(environment.getProperty("server.port")).isEqualTo("8080");
        assertThat(environment.getProperty("vaadin.launch-browser", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.properties.org.hibernate.envers.audit_table_suffix"))
                .isEqualTo("_log");
        assertThat(environment.getProperty("spring.jpa.properties.org.hibernate.envers.store_data_at_delete", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
    }

    @Test
    void br01_springdocDependencyIsDeclaredWithRequestedVersion() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom).contains("<groupId>org.springdoc</groupId>");
        assertThat(pom).contains("<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>");
        assertThat(pom).contains("<version>3.0.3</version>");
    }

    @Test
    void br03ThroughBr05_swaggerAndOpenApiRoutesAreAnonymous() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void br06Br07AndBr11_documentationRoutesDoNotWeakenApiAuthentication() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));

        mockMvc.perform(get("/api/auth/signup"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void br08ThroughBr10_openApiUsesCurrentEndpointsDtosWrapperAndBearerAuth() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ApiResponseProductResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ProductRequest").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.paths['/api/products'].post.requestBody")).isNotEmpty();
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.paths['/api/auth/login'].post.requestBody")).isNotEmpty();
        assertThat(JsonPath.<Map<String, Object>>read(body, "$.components.securitySchemes.bearerAuth")).isNotEmpty();
    }
}
