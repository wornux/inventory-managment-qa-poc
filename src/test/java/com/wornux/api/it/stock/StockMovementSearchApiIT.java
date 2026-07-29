package com.wornux.api.it.stock;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.RestAssuredApiSpecifications;
import com.wornux.api.it.support.RestAssuredApiTestBase;
import com.wornux.api.it.support.TestDataFactory;
import com.wornux.api.it.support.TokenProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StockMovementSearchApiIT extends RestAssuredApiTestBase {

    @Test
    void dateRange_usesInclusiveStartAndExclusiveEnd() {
        String token = TokenProvider.managerToken();
        long categoryId = firstCatalogId(token, ApiPaths.CATEGORIES);
        long productId = createProduct(TestDataFactory.product(categoryId))
                .jsonPath()
                .getLong("data.id");

        try {
            var movement = given().spec(RestAssuredApiSpecifications.authenticate(token))
                    .body(TestDataFactory.stockMovement(productId, "PURCHASE", 1, "date boundary"))
                    .when()
                    .post(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectSuccess(201))
                    .extract()
                    .response();
            long movementId = movement.jsonPath().getLong("data.id");
            Instant createdAt = Instant.parse(movement.jsonPath().getString("data.createdAt"));

            given().spec(RestAssuredApiSpecifications.authenticate(token))
                    .queryParam("productId", productId)
                    .queryParam("createdFrom", createdAt.toString())
                    .when()
                    .get(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectSuccess(200))
                    .body("data.id", hasItem((int) movementId));

            given().spec(RestAssuredApiSpecifications.authenticate(token))
                    .queryParam("productId", productId)
                    .queryParam("createdTo", createdAt.toString())
                    .when()
                    .get(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectSuccess(200))
                    .body("data", empty());
        } finally {
            deleteProduct(productId);
        }
    }
}
