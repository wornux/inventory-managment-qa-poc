package com.wornux.api.it.products;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TestDataFactory;
import com.wornux.api.it.support.TokenProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ProductUpdateDeleteApiIT extends MockMvcApiTestBase {

    @Test
    void validUpdate_persistsNewStateAndRejectsStaleVersion() throws Exception {
        String token = TokenProvider.managerToken();
        Map<String, Object> original = TestDataFactory.product(firstCatalogId(token, ApiPaths.CATEGORIES));
        MvcResult created = createProduct(original);
        long productId = dataLong(created, "id");
        long originalVersion = dataLong(created, "version");
        Map<String, Object> update = TestDataFactory.copy(original);
        update.put("name", original.get("name") + " updated");
        update.put("quantityOnHand", 125);
        update.put("version", originalVersion);

        try {
            expectSuccess(
                            authenticate(put(ApiPaths.PRODUCT, productId)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(json(update)),
                                    token),
                            200)
                    .andExpect(jsonPath("$.data.name").value(update.get("name")))
                    .andExpect(jsonPath("$.data.quantityOnHand").value(125));

            expectSuccess(authenticate(get(ApiPaths.PRODUCT, productId), token), 200)
                    .andExpect(jsonPath("$.data.name").value(update.get("name")))
                    .andExpect(jsonPath("$.data.quantityOnHand").value(125));

            expectFailure(
                    authenticate(put(ApiPaths.PRODUCT, productId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(update)),
                            token),
                    409);
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void viewerCannotUpdateProduct() throws Exception {
        String managerToken = TokenProvider.managerToken();
        Map<String, Object> request = TestDataFactory.product(firstCatalogId(managerToken, ApiPaths.CATEGORIES));
        MvcResult created = createProduct(request);
        long productId = dataLong(created, "id");
        request.put("version", dataLong(created, "version"));

        try {
            expectFailure(
                    authenticate(put(ApiPaths.PRODUCT, productId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(request)),
                            TokenProvider.viewerToken()),
                    403);
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void deleteProduct_makesResourceUnavailable() throws Exception {
        String token = TokenProvider.managerToken();
        Map<String, Object> request = TestDataFactory.product(firstCatalogId(token, ApiPaths.CATEGORIES));
        long productId = dataLong(createProduct(request), "id");

        deleteProduct(productId);

        expectFailure(authenticate(get(ApiPaths.PRODUCT, productId), token), 404);
    }

    @Test
    void deleteNonexistentProduct_returnsNotFound() throws Exception {
        expectFailure(
                authenticate(delete(ApiPaths.PRODUCT, Long.MAX_VALUE), TokenProvider.managerToken()),
                404);
    }
}
