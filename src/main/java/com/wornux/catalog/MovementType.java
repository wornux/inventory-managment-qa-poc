package com.wornux.catalog;

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
}
