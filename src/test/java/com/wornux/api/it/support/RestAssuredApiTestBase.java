package com.wornux.api.it.support;

import static io.restassured.RestAssured.given;

import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=api-test",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/prod",
            "vaadin.launch-browser=false"
        })
public abstract class RestAssuredApiTestBase extends AbstractApiTestBase<Response> {

    @LocalServerPort
    private int serverPort;

    @BeforeEach
    void configureRestAssuredTarget() {
        RestAssuredApiSpecifications.useBaseUrl("http://localhost:" + serverPort);
    }

    @Override
    protected long firstCatalogId(String token, String path) {
        Response response = given().spec(RestAssuredApiSpecifications.authenticate(token))
                .when()
                .get(path)
                .then()
                .spec(RestAssuredApiSpecifications.expectSuccess(200))
                .extract()
                .response();
        Number id = response.jsonPath().get("data[0].id");

        if (id == null) {
            throw new IllegalStateException("API test target has no active catalog fixture at " + path);
        }

        return id.longValue();
    }

    @Override
    protected Response createProduct(Map<String, Object> request) {
        return given().spec(RestAssuredApiSpecifications.authenticate(TokenProvider.managerToken()))
                .body(request)
                .when()
                .post(ApiPaths.PRODUCTS)
                .then()
                .spec(RestAssuredApiSpecifications.expectSuccess(201))
                .extract()
                .response();
    }

    @Override
    protected Response getProduct(String token, long productId) {
        return given().spec(RestAssuredApiSpecifications.authenticate(token))
                .pathParam("id", productId)
                .when()
                .get(ApiPaths.PRODUCT)
                .then()
                .spec(RestAssuredApiSpecifications.expectSuccess(200))
                .extract()
                .response();
    }

    @Override
    protected void deleteProduct(long productId) {
        given().spec(RestAssuredApiSpecifications.authenticate(TokenProvider.managerToken()))
                .pathParam("id", productId)
                .when()
                .delete(ApiPaths.PRODUCT)
                .then()
                .spec(RestAssuredApiSpecifications.expectSuccess(200));
    }
}
