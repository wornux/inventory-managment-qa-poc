package com.wornux.api.it.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class OpenApiContractApiIT extends MockMvcApiTestBase {

    private static final Set<String> MOVEMENT_TYPES = Set.of(
            "PURCHASE",
            "SALE",
            "RETURN_IN",
            "RETURN_OUT",
            "ADJUSTMENT_IN",
            "ADJUSTMENT_OUT",
            "INITIAL_STOCK",
            "DAMAGED",
            "LOST");

    @Test
    void publishedContract_containsEveryExposedOperationAndItsSuccessStatus() throws Exception {
        Map<String, Object> contract = contract();
        Map<String, Object> paths = child(contract, "paths");

        assertResponse(paths, ApiPaths.PRODUCTS, "get", "200");
        assertResponse(paths, ApiPaths.PRODUCTS, "post", "201");
        assertResponse(paths, ApiPaths.PRODUCT, "get", "200");
        assertResponse(paths, ApiPaths.PRODUCT, "put", "200");
        assertResponse(paths, ApiPaths.PRODUCT, "delete", "200");
        assertResponse(paths, ApiPaths.STOCK_MOVEMENTS, "get", "200");
        assertResponse(paths, ApiPaths.STOCK_MOVEMENTS, "post", "201");
        assertResponse(paths, ApiPaths.CATEGORIES, "get", "200");
        assertResponse(paths, ApiPaths.SUPPLIERS, "get", "200");
        assertResponse(paths, ApiPaths.CURRENT_USER_PERMISSIONS, "get", "200");
    }

    @Test
    void publishedProductRequestSchema_containsRequiredFieldsAndNumericBoundaries() throws Exception {
        Map<String, Object> schema = schema(contract(), "ProductRequestDto");
        List<String> required = list(schema, "required");
        Map<String, Object> properties = child(schema, "properties");

        assertThat(required)
                .containsExactlyInAnyOrder("sku", "name", "unitPrice", "quantityOnHand", "minimumStock", "categoryId");
        assertThat(child(properties, "sku")).containsEntry("minLength", 1);
        assertThat(child(properties, "name")).containsEntry("minLength", 1);
        assertThat(number(child(properties, "unitPrice"), "minimum").doubleValue())
                .isZero();
        assertThat(number(child(properties, "quantityOnHand"), "minimum").intValue())
                .isZero();
        assertThat(number(child(properties, "minimumStock"), "minimum").intValue())
                .isZero();
    }

    @Test
    void publishedStockMovementSchema_containsEveryDocumentedEnumValue() throws Exception {
        Map<String, Object> schema = schema(contract(), "StockMovementRequestDto");
        Map<String, Object> movementType = child(child(schema, "properties"), "movementType");

        assertThat(list(movementType, "enum")).containsExactlyInAnyOrderElementsOf(MOVEMENT_TYPES);
        assertThat(list(schema, "required")).containsExactlyInAnyOrder("productId", "movementType", "quantityDelta");
    }

    private Map<String, Object> contract() throws Exception {
        return responseBody(request(get(ApiPaths.OPEN_API))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn());
    }

    private static Map<String, Object> schema(Map<String, Object> contract, String name) {
        return child(child(child(contract, "components"), "schemas"), name);
    }

    private static void assertResponse(Map<String, Object> paths, String path, String operation, String responseCode) {
        Map<String, Object> responses = child(child(child(paths, path), operation), "responses");

        assertThat(responses).as(operation.toUpperCase() + " " + path).containsKey(responseCode);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String name) {
        return (Map<String, Object>) parent.get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> parent, String name) {
        return (List<String>) parent.get(name);
    }

    private static Number number(Map<String, Object> parent, String name) {
        return (Number) parent.get(name);
    }
}
