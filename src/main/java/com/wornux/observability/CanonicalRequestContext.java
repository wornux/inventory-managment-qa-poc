package com.wornux.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CanonicalRequestContext {

    private static final String ATTRIBUTE = CanonicalRequestContext.class.getName();
    private static final String ANONYMOUS = "anonymous";
    private static final String UNAVAILABLE = "unavailable";

    private final long startNanos = System.nanoTime();
    private final String correlationId;
    private final AtomicBoolean emitted = new AtomicBoolean();
    private final AtomicBoolean asyncStarted = new AtomicBoolean();
    private final AtomicBoolean authenticationFailureCounted = new AtomicBoolean();

    private volatile String traceId = UNAVAILABLE;
    private volatile String spanId = UNAVAILABLE;
    private volatile String user = ANONYMOUS;
    private volatile String endpoint = "unmatched";
    private volatile String requestKind = "unmatched";
    private volatile String errorType;
    private volatile String authenticationFailure;
    private volatile boolean authorizationFailure;
    private volatile boolean forcedServerError;

    private CanonicalRequestContext(String correlationId) {
        this.correlationId = correlationId;
    }

    static CanonicalRequestContext getOrCreate(HttpServletRequest request, String correlationId) {
        CanonicalRequestContext existing = current(request).orElse(null);
        if (existing != null) {
            return existing;
        }

        CanonicalRequestContext created = new CanonicalRequestContext(correlationId);
        request.setAttribute(ATTRIBUTE, created);

        return created;
    }

    public static Optional<CanonicalRequestContext> current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);

        return value instanceof CanonicalRequestContext context ? Optional.of(context) : Optional.empty();
    }

    public static void authenticationFailure(HttpServletRequest request, String code) {
        current(request).ifPresent(context -> context.authenticationFailure = bounded(code, 64, "authentication"));
    }

    public static boolean countAuthenticationFailure(HttpServletRequest request) {
        return current(request)
                .map(context -> context.authenticationFailureCounted.compareAndSet(false, true))
                .orElse(true);
    }

    public static void authorizationFailure(HttpServletRequest request) {
        current(request).ifPresent(context -> context.authorizationFailure = true);
    }

    public static void error(HttpServletRequest request, Throwable error) {
        current(request).ifPresent(context -> context.error(error));
    }

    void capture(Authentication authentication, Tracer tracer) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String subject = subject(authentication);
            if (subject != null && !subject.isBlank()) {
                user = bounded(subject, 128, ANONYMOUS);
            }
        }

        Span span = tracer.currentSpan();
        if (span != null) {
            traceId = bounded(span.context().traceId(), 64, UNAVAILABLE);
            spanId = bounded(span.context().spanId(), 32, UNAVAILABLE);
        }
    }

    void endpoint(String endpoint, String requestKind) {
        this.endpoint = bounded(endpoint, 256, "unmatched");
        this.requestKind = bounded(requestKind, 32, "unmatched");
    }

    void error(Throwable error) {
        if (errorType == null && error != null) {
            errorType = bounded(error.getClass().getSimpleName(), 64, "RuntimeException");
        }
    }

    void escapingError(Throwable error) {
        error(error);
        forcedServerError = true;
    }

    void asyncTimeout() {
        if (errorType == null) {
            errorType = "AsyncTimeout";
        }
        forcedServerError = true;
    }

    boolean markAsyncStarted() {
        return asyncStarted.compareAndSet(false, true);
    }

    boolean isAsyncStarted() {
        return asyncStarted.get();
    }

    boolean markEmitted() {
        return emitted.compareAndSet(false, true);
    }

    long durationMs() {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    String correlationId() {
        return correlationId;
    }

    String traceId() {
        return traceId;
    }

    String spanId() {
        return spanId;
    }

    String user() {
        return user;
    }

    String endpoint() {
        return endpoint;
    }

    String requestKind() {
        return requestKind;
    }

    String errorType() {
        return errorType;
    }

    String authenticationFailure() {
        return authenticationFailure;
    }

    boolean authorizationFailure() {
        return authorizationFailure;
    }

    boolean forcedServerError() {
        return forcedServerError;
    }

    private static String subject(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getSubject();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getSubject();
        }
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }

        return null;
    }

    private static String bounded(String value, int limit, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        String sanitized = value.replaceAll("[\\p{Cntrl}]", "_");

        return sanitized.length() <= limit ? sanitized : sanitized.substring(0, limit);
    }
}
