package com.wornux.api.it.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TestDataFactory;
import com.wornux.api.it.support.TokenProvider;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ProductCreateApiIT extends MockMvcApiTestBase {

    @Test
    void validProduct_returnsContractAndPersistsProduct() throws Exception {
        String managerToken = TokenProvider.managerToken();
        Map<String, Object> request = TestDataFactory.product(firstCatalogId(managerToken, ApiPaths.CATEGORIES));
        long productId = -1;

        try {
            MvcResult response = expectSuccess(
                            authenticate(post(ApiPaths.PRODUCTS)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(json(request)),
                                    managerToken),
                            201)
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.data.id").isNumber())
                    .andExpect(jsonPath("$.data.sku").value(request.get("sku")))
                    .andExpect(jsonPath("$.data.name").value(request.get("name")))
                    .andExpect(jsonPath("$.data.quantityOnHand").value(100))
                    .andReturn();
            productId = dataLong(response, "id");

            Map<String, Object> persisted = dataObject(getProduct(managerToken, productId));

            assertThat(persisted.get("sku")).isEqualTo(request.get("sku"));
            assertThat(persisted.get("quantityOnHand")).isEqualTo(100);
        } finally {
            if (productId > 0) {
                deleteProduct(productId);
            }
        }
    }

    @ParameterizedTest(name = "{0} product payload returns 400")
    @EnumSource(InvalidProduct.class)
    void contractValidationViolation_returnsBadRequest(InvalidProduct invalidProduct) throws Exception {
        String token = TokenProvider.managerToken();
        Map<String, Object> request = TestDataFactory.product(firstCatalogId(token, ApiPaths.CATEGORIES));
        invalidProduct.apply(request);

        expectFailure(
                authenticate(post(ApiPaths.PRODUCTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(request)),
                        token),
                400);
    }

    @Test
    void malformedJson_returnsBadRequest() throws Exception {
        expectFailure(
                authenticate(post(ApiPaths.PRODUCTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sku\":"),
                        TokenProvider.managerToken()),
                400);
    }

    @Test
    void nonexistentCategory_returnsBusinessError() throws Exception {
        Map<String, Object> request = TestDataFactory.product(Long.MAX_VALUE);

        expectFailure(
                authenticate(post(ApiPaths.PRODUCTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(request)),
                        TokenProvider.managerToken()),
                400);
    }

    @Test
    void duplicateSku_returnsConflict() throws Exception {
        String token = TokenProvider.managerToken();
        Map<String, Object> request = TestDataFactory.product(firstCatalogId(token, ApiPaths.CATEGORIES));
        long productId = dataLong(createProduct(request), "id");
        Map<String, Object> duplicate = TestDataFactory.product(firstCatalogId(token, ApiPaths.CATEGORIES));
        duplicate.put("sku", request.get("sku"));

        try {
            expectFailure(
                    authenticate(post(ApiPaths.PRODUCTS)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(duplicate)),
                            token),
                    409);
        } finally {
            deleteProduct(productId);
        }
    }

    @Test
    void viewerToken_returnsForbiddenWithoutCreatingProduct() throws Exception {
        String managerToken = TokenProvider.managerToken();
        Map<String, Object> request = TestDataFactory.product(firstCatalogId(managerToken, ApiPaths.CATEGORIES));

        expectFailure(
                authenticate(post(ApiPaths.PRODUCTS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(request)),
                        TokenProvider.viewerToken()),
                403);
    }

    private enum InvalidProduct {
        MISSING_SKU {
            @Override
            void apply(Map<String, Object> request) {
                request.remove("sku");
            }
        },
        BLANK_NAME {
            @Override
            void apply(Map<String, Object> request) {
                request.put("name", " ");
            }
        },
        NEGATIVE_UNIT_PRICE {
            @Override
            void apply(Map<String, Object> request) {
                request.put("unitPrice", new BigDecimal("-0.01"));
            }
        },
        NEGATIVE_QUANTITY {
            @Override
            void apply(Map<String, Object> request) {
                request.put("quantityOnHand", -1);
            }
        },
        NEGATIVE_MINIMUM_STOCK {
            @Override
            void apply(Map<String, Object> request) {
                request.put("minimumStock", -1);
            }
        },
        WRONG_QUANTITY_TYPE {
            @Override
            void apply(Map<String, Object> request) {
                request.put("quantityOnHand", "many");
            }
        },
        MISSING_CATEGORY {
            @Override
            void apply(Map<String, Object> request) {
                request.remove("categoryId");
            }
        };

        abstract void apply(Map<String, Object> request);
    }
}
