package com.wornux.api;

import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

public abstract class AbstractRestController {

    protected <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    protected <T> ResponseEntity<ApiResponse<List<T>>> ok(String message, Page<T> page) {
        return ResponseEntity.ok(ApiResponse.success(message, page.getContent(), PageResponse.from(page)));
    }

    protected <T> ResponseEntity<ApiResponse<T>> created(String message, URI location, T data) {
        return ResponseEntity.created(location).body(ApiResponse.success(message, data));
    }

    protected ResponseEntity<ApiResponse<Void>> noContentMessage(String message) {
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }
}
