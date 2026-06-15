package com.wornux.catalog;

import jakarta.validation.constraints.NotNull;

public class StockMovementRequest {

    @NotNull(message = "Product is required.")
    private Long productId;

    @NotNull(message = "Movement type is required.")
    private MovementType movementType;

    @NotNull(message = "Quantity delta is required.")
    private Integer quantityDelta;

    private String reason;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public Integer getQuantityDelta() {
        return quantityDelta;
    }

    public void setQuantityDelta(Integer quantityDelta) {
        this.quantityDelta = quantityDelta;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
