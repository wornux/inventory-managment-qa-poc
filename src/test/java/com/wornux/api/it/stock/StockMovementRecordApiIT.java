package com.wornux.api.it.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TestDataFactory;
import com.wornux.api.it.support.TokenProvider;
import com.wornux.catalog.MovementType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class StockMovementRecordApiIT extends MockMvcApiTestBase {

    @Test
    void purchase_updatesProductAndCreatesLedgerEntry() throws Exception {
        String token = TokenProvider.managerToken();
        long productId = createProduct(token);

        try {
            MvcResult movement = expectSuccess(
                            authenticate(
                                    post(ApiPaths.STOCK_MOVEMENTS)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(json(TestDataFactory.stockMovement(
                                                    productId, "PURCHASE", 10, "API test purchase"))),
                                    token),
                            201)
                    .andExpect(jsonPath("$.data.product.id").value(productId))
                    .andExpect(jsonPath("$.data.movementType").value("PURCHASE"))
                    .andExpect(jsonPath("$.data.quantityDelta").value(10))
                    .andReturn();
            long movementId = dataLong(movement, "id");

            assertThat(dataLong(getProduct(token, productId), "quantityOnHand")).isEqualTo(110);

            expectSuccess(
                            authenticate(
                                    get(ApiPaths.STOCK_MOVEMENTS)
                                            .queryParam("productId", Long.toString(productId))
                                            .queryParam("movementType", "PURCHASE"),
                                    token),
                            200)
                    .andExpect(jsonPath("$.data[*].id", hasItem((int) movementId)));
        } finally {
            deleteProduct(productId);
        }
    }

    @ParameterizedTest
    @EnumSource(MovementType.class)
    void everyDocumentedMovementType_isAccepted(MovementType movementType) throws Exception {
        String token = TokenProvider.managerToken();
        long productId = createProduct(token);
        int delta = movementType.isPositive() ? 1 : -1;

        try {
            expectSuccess(
                            authenticate(
                                    post(ApiPaths.STOCK_MOVEMENTS)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(json(TestDataFactory.stockMovement(
                                                    productId, movementType.name(), delta, "API enum contract"))),
                                    token),
                            201)
                    .andExpect(jsonPath("$.data.movementType").value(movementType.name()))
                    .andExpect(jsonPath("$.data.quantityDelta").value(delta));
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void invalidMovementType_returnsBadRequest() throws Exception {
        String token = TokenProvider.managerToken();
        long productId = createProduct(token);

        try {
            expectFailure(
                    authenticate(
                            post(ApiPaths.STOCK_MOVEMENTS)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(
                                            TestDataFactory.stockMovement(productId, "TRANSFER", 1, "invalid enum"))),
                            token),
                    400);
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void insufficientStock_returnsBusinessErrorWithoutChangingQuantity() throws Exception {
        String token = TokenProvider.managerToken();
        long productId = createProduct(token);

        try {
            expectFailure(
                    authenticate(
                            post(ApiPaths.STOCK_MOVEMENTS)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(
                                            TestDataFactory.stockMovement(productId, "SALE", -101, "too much stock"))),
                            token),
                    400);

            assertThat(dataLong(getProduct(token, productId), "quantityOnHand")).isEqualTo(100);
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void viewerCannotRecordMovement() throws Exception {
        String managerToken = TokenProvider.managerToken();
        long productId = createProduct(managerToken);

        try {
            expectFailure(
                    authenticate(
                            post(ApiPaths.STOCK_MOVEMENTS)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            json(TestDataFactory.stockMovement(productId, "PURCHASE", 1, "forbidden"))),
                            TokenProvider.viewerToken()),
                    403);
        } finally {
            deleteProduct(productId);
        }
    }

    private long createProduct(String token) throws Exception {
        long categoryId = firstCatalogId(token, ApiPaths.CATEGORIES);
        Map<String, Object> request = TestDataFactory.product(categoryId);

        return dataLong(createProduct(request), "id");
    }
}
