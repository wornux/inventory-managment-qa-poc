package com.wornux.e2e.page;

public record ProductDraft(
        String sku,
        String name,
        String description,
        String unitPrice,
        String quantity,
        String minimumStock,
        String category,
        String supplier) {

    public static ProductDraft lowStock(String sku, String name, String category, String supplier) {
        return new ProductDraft(sku, name, "Created by the E2E suite.", "25.50", "3", "5", category, supplier);
    }

    public String label() {
        return sku + " - " + name;
    }

    public ProductDraft renamed(String newName) {
        return new ProductDraft(sku, newName, description, unitPrice, quantity, minimumStock, category, supplier);
    }
}
