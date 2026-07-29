package com.wornux.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wornux.catalog.ProductException;
import com.wornux.catalog.StockMovementException;
import io.swagger.v3.oas.models.security.SecurityScheme;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiContractsTest extends AbstractRestController {

    @Test
    void responseFactoriesAndControllerHelpersPreserveTheirContracts() {
        var success = ApiResponse.success("ok", 7);

        assertThat(success).isEqualTo(new ApiResponse<>(true, "ok", 7, List.of(), null));

        var page = new PageImpl<>(List.of("a"), PageRequest.of(2, 1), 4);

        assertThat(PageResponse.from(page)).isEqualTo(new PageResponse(2, 1, 4, 4));
        assertThat(ok("listed", page).getBody().page()).isEqualTo(PageResponse.from(page));
        assertThat(ok("found", "x").getBody().data()).isEqualTo("x");
        assertThat(created("made", URI.create("/x/1"), 1).getHeaders().getLocation())
                .hasPath("/x/1");
        assertThat(noContentMessage("gone").getBody()).isEqualTo(ApiResponse.success("gone", null));

        var errors = List.of(new ApiErrorResponse("name", "required"));

        assertThat(ApiResponse.failure("bad", errors)).isEqualTo(new ApiResponse<>(false, "bad", null, errors, null));
    }

    @Test
    void openApiConfigurationExposesExpectedContract() {
        var issuerUri = "http://localhost:7777/realms/wornux";
        var api = new OpenApiConfig().openAPI(issuerUri);

        assertThat(api.getInfo().getTitle()).isEqualTo("QA Final Project API");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.0");

        var scheme = api.getComponents().getSecuritySchemes().get(OpenApiConfig.OAUTH2_SCHEME);

        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.OAUTH2);
        assertThat(scheme.getFlows().getAuthorizationCode().getAuthorizationUrl())
                .isEqualTo(issuerUri + "/protocol/openid-connect/auth");
        assertThat(scheme.getFlows().getAuthorizationCode().getTokenUrl())
                .isEqualTo(issuerUri + "/protocol/openid-connect/token");
        assertThat(scheme.getFlows().getAuthorizationCode().getScopes()).containsKeys("openid", "profile", "email");
    }

    @Test
    void exceptionHandlerMapsValidationAndSecurityFailures() {
        var handler = new RestExceptionHandler();
        var request = new MockHttpServletRequest();
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "sku", "required"));

        var validation = handler.methodArgumentNotValid(new MethodArgumentNotValidException(null, binding), request);

        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(validation.getBody().errors()).containsExactly(new ApiErrorResponse("sku", "required"));

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("quantity");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("positive");

        var constrained = handler.constraintViolation(new ConstraintViolationException(Set.of(violation)), request);

        assertThat(constrained.getBody().errors()).containsExactly(new ApiErrorResponse("quantity", "positive"));
        assertThat(handler.authentication(new BadCredentialsException("bad"), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.accessDenied(new AccessDeniedException("no"), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.runtime(new RuntimeException("boom"), request)
                        .getBody()
                        .message())
                .isEqualTo("Unexpected API error.");
    }

    @Test
    void exceptionHandlerMapsMalformedRequestsAndStockMovementFailures() {
        var handler = new RestExceptionHandler();
        var request = new MockHttpServletRequest();
        var mismatch = new MethodArgumentTypeMismatchException(
                "bad", Instant.class, "createdFrom", null, new IllegalArgumentException("bad"));

        assertThat(handler.methodArgumentTypeMismatch(mismatch, request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.methodArgumentTypeMismatch(mismatch, request)
                        .getBody()
                        .errors()
                        .getFirst()
                        .field())
                .isEqualTo("createdFrom");

        var unreadable = new HttpMessageNotReadableException("bad json", mock(HttpInputMessage.class));

        assertThat(handler.httpMessageNotReadable(unreadable, request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.stockMovement(new StockMovementException("Invalid movement."), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.stockMovement(
                                new StockMovementException(
                                        "Failed to save movement.", new DataIntegrityViolationException("database")),
                                request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void productErrorsSelectNotFoundConflictAndBadRequestWithoutCaseSensitivity() {
        var handler = new RestExceptionHandler();
        var request = new MockHttpServletRequest();

        assertThat(handler.product(new ProductException("Product NOT FOUND"), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.product(new ProductException("SKU already exists"), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.product(new ProductException("Updated by another user"), request)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.product(new ProductException("invalid"), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.product(new ProductException(null), request).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
