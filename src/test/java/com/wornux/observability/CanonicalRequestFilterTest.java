package com.wornux.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class CanonicalRequestFilterTest {

    private final CanonicalRequestFilter filter = new CanonicalRequestFilter(mock(Tracer.class));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @MethodSource("classifiedPaths")
    void completedRequestClassifiesNonMvcPaths(String path, String kind) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> {});

        CanonicalRequestContext context =
                CanonicalRequestContext.current(request).orElseThrow();
        assertThat(context.endpoint()).isEqualTo(kind);
        assertThat(context.requestKind()).isEqualTo(kind);
        assertThat(response.getHeader("X-Correlation-ID")).isNotBlank();
        assertThat(context.markEmitted()).isFalse();
    }

    static Stream<Arguments> classifiedPaths() {
        return Stream.of(
                Arguments.of("/actuator/health", "actuator"),
                Arguments.of("/api/products", "api"),
                Arguments.of("/oauth2/authorization/keycloak", "oauth2"),
                Arguments.of("/login/oauth2/code/keycloak", "oauth2"),
                Arguments.of("/login", "oauth2"),
                Arguments.of("/UIDL/x", "vaadin"),
                Arguments.of("/HEARTBEAT/x", "vaadin"),
                Arguments.of("/VAADIN/x", "vaadin"),
                Arguments.of("/connect/x", "vaadin"),
                Arguments.of("/styles/x", "static"),
                Arguments.of("/icons/x", "static"),
                Arguments.of("/frontend/x", "static"),
                Arguments.of("/webjars/x", "static"),
                Arguments.of("/favicon.ico", "static"),
                Arguments.of("/other", "unmatched"));
    }

    @Test
    void mvcPatternAndValidCorrelationIdArePreservedAcrossDispatches() throws Exception {
        var request = new MockHttpServletRequest("POST", "/products/7");
        request.addHeader("X-Correlation-ID", "client-id_7");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/products/{id}");
        var response = new MockHttpServletResponse();
        MDC.put("traceId", "previous-trace");
        MDC.put("spanId", "previous-span");

        filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> response.setStatus(404));
        filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> {});

        CanonicalRequestContext context =
                CanonicalRequestContext.current(request).orElseThrow();
        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("client-id_7");
        assertThat(context.endpoint()).isEqualTo("/products/{id}");
        assertThat(context.requestKind()).isEqualTo("mvc");
        assertThat(MDC.get("traceId")).isEqualTo("previous-trace");
        assertThat(MDC.get("spanId")).isEqualTo("previous-span");
    }

    @Test
    void blankMvcPatternUsesRootEndpoint() throws Exception {
        var request = new MockHttpServletRequest("GET", "/");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, " ");

        filter.doFilterInternal(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {});

        assertThat(CanonicalRequestContext.current(request).orElseThrow().endpoint())
                .isEqualTo("/");
    }

    @Test
    void escapingFailureIsRethrownAndRecordedAsServerError() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/fail");
        request.addHeader("X-Correlation-ID", "invalid id");
        var response = new MockHttpServletResponse();
        response.setStatus(503);
        FilterChain chain = mock(FilterChain.class);
        doThrow(new IllegalStateException("failure")).when(chain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                .isInstanceOf(IllegalStateException.class);

        CanonicalRequestContext context =
                CanonicalRequestContext.current(request).orElseThrow();
        assertThat(context.errorType()).isEqualTo("IllegalStateException");
        assertThat(context.forcedServerError()).isTrue();
        assertThat(response.getHeader("X-Correlation-ID")).isNotEqualTo("invalid id");
    }

    @Test
    void asyncLifecycleRegistersOnceAndCapturesTimeoutErrorAndCompletion() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/async");
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        AsyncContext asyncContext = request.startAsync(request, response);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);
        filter.doFilterInternal(request, response, chain);

        AsyncListener listener =
                ((MockAsyncContext) request.getAsyncContext()).getListeners().getFirst();
        AsyncContext restarted = mock(AsyncContext.class);
        AsyncEvent restartedEvent = new AsyncEvent(restarted);
        listener.onStartAsync(restartedEvent);
        verify(restarted).addListener(listener);
        listener.onTimeout(new AsyncEvent(asyncContext));
        listener.onError(new AsyncEvent(asyncContext, new IOException("async")));
        listener.onComplete(new AsyncEvent(asyncContext));

        CanonicalRequestContext context =
                CanonicalRequestContext.current(request).orElseThrow();
        assertThat(context.isAsyncStarted()).isTrue();
        assertThat(context.errorType()).isEqualTo("AsyncTimeout");
        assertThat(context.forcedServerError()).isTrue();
        assertThat(context.markEmitted()).isFalse();
    }

    @Test
    void asyncRegistrationRaceStillEmitsRequest() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/async");
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        request.startAsync(request, response);
        request.getAsyncContext().complete();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
                    request.startAsync();
                    request.getAsyncContext().complete();
                    return null;
                })
                .when(chain)
                .doFilter(any(), any());

        filter.doFilterInternal(request, response, chain);

        assertThat(CanonicalRequestContext.current(request).orElseThrow().markEmitted())
                .isFalse();
    }

    @Test
    void completedAsyncContextIsNotEmittedByRedispatch() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/async");
        CanonicalRequestContext context = CanonicalRequestContext.getOrCreate(request, "id");
        context.markAsyncStarted();

        filter.doFilterInternal(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {});

        assertThat(context.markEmitted()).isTrue();
    }

    @Test
    void listenerRegistrationRaceStillEmitsRequest() throws Exception {
        AtomicReference<Object> attribute = new AtomicReference<>();
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        when(request.getAttribute(anyString())).thenAnswer(invocation -> attribute.get());
        doAnswer(invocation -> {
                    attribute.set(invocation.getArgument(1));
                    return null;
                })
                .when(request)
                .setAttribute(anyString(), any());
        when(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/async");
        when(request.getMethod()).thenReturn("GET");
        when(request.isAsyncStarted()).thenReturn(true);
        when(request.getAsyncContext()).thenThrow(new IllegalStateException("completed"));

        filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> {});

        assertThat(((CanonicalRequestContext) attribute.get()).markEmitted()).isFalse();
    }

    @Test
    void completionIncludesSecurityFailureDetails() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/secure");

        filter.doFilterInternal(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {
            CanonicalRequestContext.authenticationFailure(request, "invalid_token");
            CanonicalRequestContext.authorizationFailure(request);
        });

        CanonicalRequestContext context =
                CanonicalRequestContext.current(request).orElseThrow();
        assertThat(context.authenticationFailure()).isEqualTo("invalid_token");
        assertThat(context.authorizationFailure()).isTrue();
    }

    @Test
    void loggingFailureDoesNotEscapeFilterOrLeakMdc() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/products");
        var response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenThrow(new IllegalStateException("broken response"));

        filter.doFilterInternal(request, response, (ignoredRequest, ignoredResponse) -> {});

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    void filterParticipatesInAsyncAndErrorDispatches() {
        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
        assertThat(filter.shouldNotFilterErrorDispatch()).isFalse();
    }
}
