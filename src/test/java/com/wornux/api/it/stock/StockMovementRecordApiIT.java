package com.wornux.api.it.stock;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.RestAssuredApiSpecifications;
import com.wornux.api.it.support.RestAssuredApiTestBase;
import com.wornux.api.it.support.TestDataFactory;
import com.wornux.api.it.support.TokenProvider;
import com.wornux.catalog.MovementType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StockMovementRecordApiIT extends RestAssuredApiTestBase {

    @Test
    void purchase_updatesProductAndCreatesLedgerEntry() {
        String token = TokenProvider.managerToken();
        long productId = createProduct(token);

        try {
            var movement = given().spec(RestAssuredApiSpecifications.authenticate(token))
                    .body(TestDataFactory.stockMovement(productId, "PURCHASE", 10, "API test purchase"))
                    .when()
                    .post(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectSuccess(201))
                    .body("data.product.id", equalTo((int) productId))
                    .body("data.movementType", equalTo("PURCHASE"))
                    .body("data.quantityDelta", equalTo(10))
                    .extract()
                    .response();
            long movementId = movement.jsonPath().getLong("data.id");

            getProduct(token, productId).then().body("data.quantityOnHand", equalTo(110));

            given().spec(RestAssuredApiSpecifications.authenticate(token))
                    .queryParam("productId", productId)
                    .queryParam("movementType", "PURCHASE")
                    .when()
                    .get(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectSuccess(200))
                    .body("data.id", hasItem((int) movementId));
        } finally {
            deleteProduct(productId);
        }
    }

    @ParameterizedTest
    @EnumSource(MovementType.class)
    void everyDocumentedMovementType_isAccepted(MovementType movementType) {
        String token = TokenProvider.managerToken();
        long productId = createProduct(token);
        int delta = movementType.isPositive() ? 1 : -1;

        try {
            given().spec(RestAssuredApiSpecifications.authenticate(token))
                    .body(TestDataFactory.stockMovement(productId, movementType.name(), delta, "API enum contract"))
                    .when()
                    .post(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectSuccess(201))
                    .body("data.movementType", equalTo(movementType.name()))
                    .body("data.quantityDelta", equalTo(delta));
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void invalidMovementType_returnsBadRequest() {
        String token = TokenProvider.managerToken();
        long productId = createProduct(token);

        try {
            given().spec(RestAssuredApiSpecifications.authenticate(token))
                    .body(TestDataFactory.stockMovement(productId, "TRANSFER", 1, "invalid enum"))
                    .when()
                    .post(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectFailure(400));
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void insufficientStock_returnsBusinessErrorWithoutChangingQuantity() {
        String token = TokenProvider.managerToken();
        long productId = createProduct(token);

        try {
            given().spec(RestAssuredApiSpecifications.authenticate(token))
                    .body(TestDataFactory.stockMovement(productId, "SALE", -101, "too much stock"))
                    .when()
                    .post(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectFailure(400));

            assertThat(getProduct(token, productId).jsonPath().getInt("data.quantityOnHand"))
                    .isEqualTo(100);
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void viewerCannotRecordMovement() {
        String managerToken = TokenProvider.managerToken();
        long productId = createProduct(managerToken);

        try {
            given().spec(RestAssuredApiSpecifications.authenticate(TokenProvider.viewerToken()))
                    .body(TestDataFactory.stockMovement(productId, "PURCHASE", 1, "forbidden"))
                    .when()
                    .post(ApiPaths.STOCK_MOVEMENTS)
                    .then()
                    .spec(RestAssuredApiSpecifications.expectFailure(403));
        } finally {
            deleteProduct(productId);
        }
    }

    private long createProduct(String token) {
        long categoryId = firstCatalogId(token, ApiPaths.CATEGORIES);
        Map<String, Object> request = TestDataFactory.product(categoryId);

        return createProduct(request).jsonPath().getLong("data.id");
    }
}
