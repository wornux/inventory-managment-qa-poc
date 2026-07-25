package com.wornux.security.permission;

public enum AppResource {
    PRODUCT("product", "Product"),
    CATEGORY("category", "Category"),
    SUPPLIER("supplier", "Supplier"),
    STOCK_MOVEMENT("stock-movement", "Stock movement"),
    USER("user", "User"),
    ROLE("role", "Role");

    private final String code;
    private final String label;

    AppResource(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
