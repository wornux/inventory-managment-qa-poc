package com.wornux.api;

import com.wornux.catalog.ProductException;
import com.wornux.catalog.StockMovementException;
import com.wornux.observability.CanonicalRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.wornux.api")
public class RestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> methodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiErrorResponse> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toError)
                .toList();

        return failure(request, exception, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> constraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<ApiErrorResponse> errors = exception.getConstraintViolations().stream()
                .map(violation ->
                        new ApiErrorResponse(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        return failure(request, exception, HttpStatus.BAD_REQUEST, "Request validation failed.", errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> methodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return failure(
                request,
                exception,
                HttpStatus.BAD_REQUEST,
                "Request validation failed.",
                List.of(new ApiErrorResponse(exception.getName(), "Invalid value.")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> httpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return failure(
                request,
                exception,
                HttpStatus.BAD_REQUEST,
                "Request validation failed.",
                List.of(new ApiErrorResponse(null, "Request body is not valid JSON.")));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiResponse<Void>> authentication(AuthenticationException exception, HttpServletRequest request) {
        CanonicalRequestContext.authenticationFailure(request, "api_authentication");

        return failure(
                request,
                exception,
                HttpStatus.UNAUTHORIZED,
                "Authentication failed.",
                List.of(error("A valid bearer token is required.")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        CanonicalRequestContext.authorizationFailure(request);

        return failure(
                request, exception, HttpStatus.FORBIDDEN, "Access denied.", List.of(error("Permission is required.")));
    }

    @ExceptionHandler(ProductException.class)
    ResponseEntity<ApiResponse<Void>> product(ProductException exception, HttpServletRequest request) {
        HttpStatus status = productStatus(exception.getMessage());

        return failure(request, exception, status, exception.getMessage(), List.of(error(exception.getMessage())));
    }

    @ExceptionHandler(StockMovementException.class)
    ResponseEntity<ApiResponse<Void>> stockMovement(StockMovementException exception, HttpServletRequest request) {
        HttpStatus status = exception.getCause() instanceof DataAccessException
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;

        return failure(request, exception, status, exception.getMessage(), List.of(error(exception.getMessage())));
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ApiResponse<Void>> runtime(RuntimeException exception, HttpServletRequest request) {
        return failure(
                request,
                exception,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected API error.",
                List.of(error("The request could not be completed.")));
    }

    private ApiErrorResponse toError(FieldError error) {
        return new ApiErrorResponse(error.getField(), error.getDefaultMessage());
    }

    private ApiErrorResponse error(String message) {
        return new ApiErrorResponse(null, message);
    }

    private ResponseEntity<ApiResponse<Void>> failure(
            HttpServletRequest request,
            Throwable exception,
            HttpStatus status,
            String message,
            List<ApiErrorResponse> errors) {
        CanonicalRequestContext.error(request, exception);

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
