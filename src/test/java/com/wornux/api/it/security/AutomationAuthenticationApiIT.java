package com.wornux.api.it.security;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TestDataFactory;
import com.wornux.api.it.support.TokenProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AutomationAuthenticationApiIT extends MockMvcApiTestBase {

    @Test
    void automationServiceAccount_isProvisionedAsInventoryViewer() throws Exception {
        String token = TokenProvider.automationToken();

        expectSuccess(authenticate(get(ApiPaths.CURRENT_USER_PERMISSIONS), token), 200)
                .andExpect(jsonPath(
                        "$.data.permissions",
                        containsInAnyOrder(
                                "product:view",
                                "category:view",
                                "supplier:view",
                                "stock-movement:view",
                                "report:view")));
    }

    @Test
    void automationServiceAccount_cannotCreateProducts() throws Exception {
        String token = TokenProvider.automationToken();
        Map<String, Object> request = TestDataFactory.product(firstCatalogId(token, ApiPaths.CATEGORIES));

        expectFailure(
                authenticate(
                        post(ApiPaths.PRODUCTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(request)),
                        token),
                403);
    }

    @Test
    void automationServiceAccount_cannotDeleteProducts() throws Exception {
        String token = TokenProvider.automationToken();

        expectFailure(authenticate(delete(ApiPaths.PRODUCT, Long.MAX_VALUE), token), 403);
    }

    @Test
    void automationServiceAccount_cannotRecordStockMovements() throws Exception {
        String managerToken = TokenProvider.managerToken();
        Map<String, Object> product = TestDataFactory.product(firstCatalogId(managerToken, ApiPaths.CATEGORIES));
        long productId = dataLong(createProduct(product), "id");

        try {
            String token = TokenProvider.automationToken();
            Map<String, Object> movement =
                    TestDataFactory.stockMovement(productId, "PURCHASE", 1, "forbidden automation request");

            expectFailure(
                    authenticate(
                            post(ApiPaths.STOCK_MOVEMENTS)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(movement)),
                            token),
                    403);
        } finally {
            deleteProduct(productId);
        }
    }
}
