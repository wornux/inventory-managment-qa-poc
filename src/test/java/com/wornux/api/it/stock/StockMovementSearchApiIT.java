package com.wornux.api.it.stock;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TestDataFactory;
import com.wornux.api.it.support.TokenProvider;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class StockMovementSearchApiIT extends MockMvcApiTestBase {

    @Test
    void dateRange_usesInclusiveStartAndExclusiveEnd() throws Exception {
        String token = TokenProvider.managerToken();
        long categoryId = firstCatalogId(token, ApiPaths.CATEGORIES);
        long productId = dataLong(createProduct(TestDataFactory.product(categoryId)), "id");

        try {
            MvcResult movement = expectSuccess(
                            authenticate(
                                    post(ApiPaths.STOCK_MOVEMENTS)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(json(TestDataFactory.stockMovement(
                                                    productId, "PURCHASE", 1, "date boundary"))),
                                    token),
                            201)
                    .andReturn();
            long movementId = dataLong(movement, "id");
            Map<String, Object> movementData = dataObject(movement);
            Instant createdAt = Instant.parse((String) movementData.get("createdAt"));

            expectSuccess(
                            authenticate(
                                    get(ApiPaths.STOCK_MOVEMENTS)
                                            .queryParam("productId", Long.toString(productId))
                                            .queryParam("createdFrom", createdAt.toString()),
                                    token),
                            200)
                    .andExpect(jsonPath("$.data[*].id", hasItem((int) movementId)));

            expectSuccess(
                            authenticate(
                                    get(ApiPaths.STOCK_MOVEMENTS)
                                            .queryParam("productId", Long.toString(productId))
                                            .queryParam("createdTo", createdAt.toString()),
                                    token),
                            200)
                    .andExpect(jsonPath("$.data", empty()));
        } finally {
            deleteProduct(productId);
        }
    }
}
