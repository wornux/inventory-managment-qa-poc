package com.wornux.api.product;

import java.math.BigDecimal;

public record ProductResponseDto(
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
        CatalogReferenceResponseDto category,
        CatalogReferenceResponseDto supplier) {}
