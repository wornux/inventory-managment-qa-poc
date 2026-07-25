package com.wornux.api.stockmovement;

import com.wornux.catalog.MovementType;
import jakarta.validation.constraints.NotNull;

public record StockMovementRequestDto(
        @NotNull(message = "Product is required.") Long productId,
        @NotNull(message = "Movement type is required.") MovementType movementType,
        @NotNull(message = "Quantity delta is required.") Integer quantityDelta,
        String reason) {}
