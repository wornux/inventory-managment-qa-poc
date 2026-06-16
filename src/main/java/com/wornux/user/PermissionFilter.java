package com.wornux.user;

public record PermissionFilter(Long resourceId, Long actionId, Boolean active) {
}
