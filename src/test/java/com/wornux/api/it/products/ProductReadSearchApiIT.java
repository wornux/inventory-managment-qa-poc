package com.wornux.api.it.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TestDataFactory;
import com.wornux.api.it.support.TokenProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class ProductReadSearchApiIT extends MockMvcApiTestBase {

    @Test
    void existingProduct_isReadableWithViewPermission() throws Exception {
        String managerToken = TokenProvider.managerToken();
        String viewerToken = TokenProvider.viewerToken();
        Map<String, Object> request = TestDataFactory.product(firstCatalogId(managerToken, ApiPaths.CATEGORIES));
        long productId = dataLong(createProduct(request), "id");

        try {
            expectSuccess(authenticate(get(ApiPaths.PRODUCT, productId), viewerToken), 200)
                    .andExpect(jsonPath("$.data.id").value(productId))
                    .andExpect(jsonPath("$.data.sku").value(request.get("sku")));
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void list_combinesTextCategoryActivePaginationAndSorting() throws Exception {
        String token = TokenProvider.managerToken();
        long categoryId = firstCatalogId(token, ApiPaths.CATEGORIES);
        Map<String, Object> request = TestDataFactory.product(categoryId);
        long productId = dataLong(createProduct(request), "id");

        try {
            MvcResult response = expectSuccess(
                            authenticate(get(ApiPaths.PRODUCTS)
                                            .queryParam("text", request.get("sku").toString())
                                            .queryParam("categoryId", Long.toString(categoryId))
                                            .queryParam("active", "true")
                                            .queryParam("page", "0")
                                            .queryParam("size", "5")
                                            .queryParam("sort", "sku,asc"),
                                    TokenProvider.viewerToken()),
                            200)
                    .andExpect(jsonPath("$.data[*].id", hasItem((int) productId)))
                    .andExpect(jsonPath("$.data[*].category.id", everyItem(equalTo((int) categoryId))))
                    .andExpect(jsonPath("$.data[*].active", everyItem(equalTo(true))))
                    .andExpect(jsonPath("$.data.length()", lessThanOrEqualTo(5)))
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.size").value(5))
                    .andReturn();

            assertThat(dataList(response)).extracting(value -> (String) value.get("sku")).isSorted();
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void nonexistentProduct_returnsNotFound() throws Exception {
        expectFailure(authenticate(get(ApiPaths.PRODUCT, Long.MAX_VALUE), TokenProvider.viewerToken()), 404);
    }

    @Test
    void malformedProductId_returnsBadRequest() throws Exception {
        expectFailure(
                authenticate(get(ApiPaths.PRODUCTS + "/not-a-number"), TokenProvider.viewerToken()),
                400);
    }

    @Test
    void invalidQueryParameter_returnsBadRequest() throws Exception {
        expectFailure(
                authenticate(get(ApiPaths.PRODUCTS).queryParam("categoryId", "not-a-number"),
                        TokenProvider.viewerToken()),
                400);
    }
}
