package com.wornux.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard API error item.")
public record ApiErrorResponse(String field, String message) {
}
