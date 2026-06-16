package com.wornux.api;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard API response wrapper.")
public record ApiResponse<T>(
        @Schema(description = "Whether the request completed successfully.")
        boolean success,
        @Schema(description = "Human-readable result message.")
        String message,
        @Schema(description = "Response payload when the request succeeds.")
        T data,
        @Schema(description = "Validation or processing errors when the request fails.")
        List<ApiErrorResponse> errors,
        @Schema(description = "Page metadata for pageable responses.")
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
