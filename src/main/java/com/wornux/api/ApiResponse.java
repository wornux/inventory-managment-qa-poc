package com.wornux.api;

import java.util.List;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<ApiErrorResponse> errors,
        PageResponse page) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, List.of(), null);
    }

    public static <T> ApiResponse<T> success(String message, T data, PageResponse page) {
        return new ApiResponse<>(true, message, data, List.of(), page);
    }

    public static <T> ApiResponse<T> failure(String message, List<ApiErrorResponse> errors) {
        return new ApiResponse<>(false, message, null, errors, null);
    }
}
