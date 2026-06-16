package com.wornux.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

@Schema(description = "Page metadata for pageable API responses.")
public record PageResponse(int number, int size, long totalElements, int totalPages) {

    public static PageResponse from(Page<?> page) {
        return new PageResponse(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
