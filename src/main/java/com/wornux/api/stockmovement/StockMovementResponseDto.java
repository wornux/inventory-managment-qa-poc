package com.wornux.api.stockmovement;

import com.wornux.catalog.MovementType;
import java.time.Instant;

public record StockMovementResponseDto(
        Long id,
        Instant createdAt,
        StockMovementProductResponseDto product,
        MovementType movementType,
        Integer quantityDelta,
        String username,
        String reason) {}
