package com.wornux.api.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProductRequestDto(
        @NotBlank(message = "SKU is required.") String sku,
        @NotBlank(message = "Product name is required.") String name,
        String description,

        @NotNull(message = "Unit price is required.")
        @DecimalMin(value = "0.00", message = "Unit price must be a positive number.")
        BigDecimal unitPrice,

        @NotNull(message = "Quantity on hand is required.")
        @Min(value = 0, message = "Quantity on hand must be zero or greater.")
        Integer quantityOnHand,

        @NotNull(message = "Minimum stock is required.")
        @Min(value = 0, message = "Minimum stock must be zero or greater.")
        Integer minimumStock,

        @NotNull(message = "Category is required.") Long categoryId,
        Long supplierId,
        boolean active,
        Long version) {}
