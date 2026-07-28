package com.wornux.e2e.page;

public record StockMovementDraft(String product, String type, String quantity, String reason) {

    public static StockMovementDraft purchase(String product, int quantity) {
        return new StockMovementDraft(product, "PURCHASE", Integer.toString(quantity), "");
    }

    public static StockMovementDraft sale(String product, int quantity) {
        return new StockMovementDraft(product, "SALE", Integer.toString(-quantity), "");
    }

    public String displayedQuantity() {
        return quantity.startsWith("-") ? quantity : "+" + quantity;
    }
}
