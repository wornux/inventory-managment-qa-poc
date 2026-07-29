package com.wornux.api.it.support;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static Map<String, Object> product(long categoryId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var request = new LinkedHashMap<String, Object>();
        request.put("sku", "API-" + suffix);
        request.put("name", "API test product " + suffix);
        request.put("description", "Disposable product created by the API test suite");
        request.put("unitPrice", new BigDecimal("25.50"));
        request.put("quantityOnHand", 100);
        request.put("minimumStock", 10);
        request.put("categoryId", categoryId);
        request.put("supplierId", null);
        request.put("active", true);
        request.put("version", null);

        return request;
    }

    public static Map<String, Object> copy(Map<String, Object> request) {
        return new LinkedHashMap<>(request);
    }

    public static Map<String, Object> stockMovement(
            long productId, String movementType, int quantityDelta, String reason) {
        var request = new LinkedHashMap<String, Object>();
        request.put("productId", productId);
        request.put("movementType", movementType);
        request.put("quantityDelta", quantityDelta);
        request.put("reason", reason);

        return request;
    }
}
