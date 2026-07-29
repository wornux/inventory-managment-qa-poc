package com.wornux.api.it.support;

public final class ApiPaths {

    public static final String CATEGORIES = "/api/categories";
    public static final String CURRENT_USER_PERMISSIONS = "/api/me/permissions";
    public static final String OPEN_API = "/v3/api-docs";
    public static final String PRODUCTS = "/api/products";
    public static final String PRODUCT = PRODUCTS + "/{id}";
    public static final String STOCK_MOVEMENTS = "/api/stock-movements";
    public static final String SUPPLIERS = "/api/suppliers";

    private ApiPaths() {}
}
