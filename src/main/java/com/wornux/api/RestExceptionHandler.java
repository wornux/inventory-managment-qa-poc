package com.wornux.api;

import com.wornux.catalog.ProductException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.wornux.api")
public class RestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> methodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ApiErrorResponse> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toError)
                .toList();

        return failure(HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> constraintViolation(ConstraintViolationException exception) {
        List<ApiErrorResponse> errors = exception.getConstraintViolations().stream()
                .map(violation ->
                        new ApiErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        return failure(HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> authentication(AuthenticationException exception) {
        return failure(HttpStatus.UNAUTHORIZED, "Authentication failed.", List.of(error(exception.getMessage())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException exception) {
        return failure(HttpStatus.FORBIDDEN, "Access denied.", List.of(error(exception.getMessage())));
    }

    @ExceptionHandler(ProductException.class)
    ResponseEntity<ApiResponse<Void>> product(ProductException exception) {
        HttpStatus status = productStatus(exception.getMessage());

        return failure(status, exception.getMessage(), List.of(error(exception.getMessage())));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiResponse<Void>> runtime(RuntimeException exception) {
        return failure(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected API error.", List.of(error(exception.getMessage())));
    }

    private ApiErrorResponse toError(FieldError error) {
        return new ApiErrorResponse(error.getField(), error.getDefaultMessage());
    }

    private ApiErrorResponse error(String message) {
        return new ApiErrorResponse(null, message);
    }

    private ResponseEntity<ApiResponse<Void>> failure(
            HttpStatus status, String message, List<ApiErrorResponse> errors) {
        return ResponseEntity.status(status).body(ApiResponse.failure(message, errors));
    }

    private HttpStatus productStatus(String message) {
        if (message != null && message.toLowerCase().contains("not found")) {
            return HttpStatus.NOT_FOUND;
        }

        if (message != null
                && (message.toLowerCase().contains("already exists")
                        || message.toLowerCase().contains("updated by another user"))) {
            return HttpStatus.CONFLICT;
        }

        return HttpStatus.BAD_REQUEST;
    }
}
