package com.wornux.catalog;

import java.util.Set;

public enum MovementType {
    PURCHASE(true, false),
    SALE(false, false),
    RETURN_IN(true, false),
    RETURN_OUT(false, false),
    ADJUSTMENT_IN(true, true),
    ADJUSTMENT_OUT(false, true),
    INITIAL_STOCK(true, false),
    DAMAGED(false, true),
    LOST(false, true);

    private static final Set<MovementType> INBOUND = Set.of(PURCHASE, RETURN_IN, ADJUSTMENT_IN, INITIAL_STOCK);

    private final boolean positive;
    private final boolean reasonRequired;

    MovementType(boolean positive, boolean reasonRequired) {
        this.positive = positive;
        this.reasonRequired = reasonRequired;
    }

    public boolean isPositive() {
        return positive;
    }

    public boolean isNegative() {
        return !positive;
    }

    public boolean isReasonRequired() {
        return reasonRequired;
    }

    public String displayName() {
        return name().replace('_', ' ');
    }

    public static boolean isInbound(MovementType movementType) {
        return INBOUND.contains(movementType);
    }
}
