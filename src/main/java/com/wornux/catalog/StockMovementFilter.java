package com.wornux.catalog;

import java.time.Instant;

public record StockMovementFilter(
        Instant createdFrom,
        Instant createdTo,
        Long productId,
        MovementType movementType,
        String username) {
}
