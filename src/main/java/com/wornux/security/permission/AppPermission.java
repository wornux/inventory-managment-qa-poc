package com.wornux.security.permission;

import java.util.Arrays;
import java.util.Optional;

public enum AppPermission {
    PRODUCT_VIEW(AppResource.PRODUCT, AppAction.VIEW),
    PRODUCT_CREATE(AppResource.PRODUCT, AppAction.CREATE),
    PRODUCT_UPDATE(AppResource.PRODUCT, AppAction.UPDATE),
    PRODUCT_DELETE(AppResource.PRODUCT, AppAction.DELETE),
    CATEGORY_VIEW(AppResource.CATEGORY, AppAction.VIEW),
    CATEGORY_CREATE(AppResource.CATEGORY, AppAction.CREATE),
    CATEGORY_UPDATE(AppResource.CATEGORY, AppAction.UPDATE),
    CATEGORY_DELETE(AppResource.CATEGORY, AppAction.DELETE),
    SUPPLIER_VIEW(AppResource.SUPPLIER, AppAction.VIEW),
    SUPPLIER_CREATE(AppResource.SUPPLIER, AppAction.CREATE),
    SUPPLIER_UPDATE(AppResource.SUPPLIER, AppAction.UPDATE),
    SUPPLIER_DELETE(AppResource.SUPPLIER, AppAction.DELETE),
    STOCK_MOVEMENT_VIEW(AppResource.STOCK_MOVEMENT, AppAction.VIEW),
    STOCK_MOVEMENT_CREATE(AppResource.STOCK_MOVEMENT, AppAction.CREATE),
    USER_VIEW(AppResource.USER, AppAction.VIEW),
    USER_CREATE(AppResource.USER, AppAction.CREATE),
    USER_UPDATE(AppResource.USER, AppAction.UPDATE),
    USER_DELETE(AppResource.USER, AppAction.DELETE),
    USER_ASSIGN(AppResource.USER, AppAction.ASSIGN),
    ROLE_VIEW(AppResource.ROLE, AppAction.VIEW),
    ROLE_CREATE(AppResource.ROLE, AppAction.CREATE),
    ROLE_UPDATE(AppResource.ROLE, AppAction.UPDATE),
    ROLE_DELETE(AppResource.ROLE, AppAction.DELETE),
    ROLE_ASSIGN(AppResource.ROLE, AppAction.ASSIGN);

    private final AppResource resource;
    private final AppAction action;
    private final String code;

    AppPermission(AppResource resource, AppAction action) {
        this.resource = resource;
        this.action = action;
        this.code = resource.code() + ":" + action.code();
    }

    public AppResource resource() {
        return resource;
    }

    public AppAction action() {
        return action;
    }

    public String code() {
        return code;
    }

    public String label() {
        return resource.label() + " · " + action.label();
    }

    public boolean grants(AppPermission requested) {
        return resource == requested.resource && action.grants(requested.action);
    }

    public static Optional<AppPermission> fromCode(String code) {
        return Arrays.stream(values())
                .filter(permission -> permission.code.equalsIgnoreCase(code))
                .findFirst();
    }
}
