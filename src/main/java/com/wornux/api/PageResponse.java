package com.wornux.api;

import org.springframework.data.domain.Page;

public record PageResponse(int number, int size, long totalElements, int totalPages) {

    public static PageResponse from(Page<?> page) {
        return new PageResponse(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
