package com.wornux.api.product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal unitPrice,
        Integer quantityOnHand,
        Integer minimumStock,
        boolean active,
        Long version,
        boolean lowStock,
        CatalogReferenceResponse category,
        CatalogReferenceResponse supplier) {
}
