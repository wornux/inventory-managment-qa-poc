package com.wornux.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class CanonicalRequestFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CanonicalRequestFilter.class);
    private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final Tracer tracer;

    public CanonicalRequestFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestedCorrelationId = request.getHeader(CORRELATION_HEADER);
        String correlationId = requestedCorrelationId != null
                        && CORRELATION_ID.matcher(requestedCorrelationId).matches()
                ? requestedCorrelationId
                : UUID.randomUUID().toString();
        CanonicalRequestContext context = CanonicalRequestContext.getOrCreate(request, correlationId);
        response.setHeader(CORRELATION_HEADER, context.correlationId());

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error exception) {
            context.escapingError(exception);
            throw exception;
        } finally {
            capture(context, request);
            captureEndpoint(context, request);

            if (request.isAsyncStarted()) {
                if (context.markAsyncStarted()) {
                    registerAsyncListener(request, response, context);
                }
            } else if (!context.isAsyncStarted()) {
                emit(context, request, response);
            }
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private void registerAsyncListener(
            HttpServletRequest request, HttpServletResponse response, CanonicalRequestContext context) {
        AsyncListener listener = new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                captureEndpoint(context, request);
                emit(context, request, response);
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                context.asyncTimeout();
            }

            @Override
            public void onError(AsyncEvent event) {
                context.escapingError(event.getThrowable());
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                event.getAsyncContext().addListener(this);
            }
        };

        try {
            request.getAsyncContext().addListener(listener);
        } catch (IllegalStateException exception) {
            emit(context, request, response);
        }
    }

    private void capture(CanonicalRequestContext context, HttpServletRequest request) {
        context.capture(SecurityContextHolder.getContext().getAuthentication(), tracer);
        captureEndpoint(context, request);
    }

    private void captureEndpoint(CanonicalRequestContext context, HttpServletRequest request) {
        Object routePattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (routePattern != null) {
            String pattern = routePattern.toString();
            context.endpoint(pattern.isBlank() ? "/" : pattern, "mvc");

            return;
        }

        String requestKind = classify(request.getRequestURI());
        context.endpoint(requestKind, requestKind);
    }

    private String classify(String path) {
        if (path.startsWith("/actuator/")) {
            return "actuator";
        }
        if (path.startsWith("/api/")) {
            return "api";
        }
        if (path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/") || path.equals("/login")) {
            return "oauth2";
        }
        if (path.startsWith("/UIDL/")
                || path.startsWith("/HEARTBEAT/")
                || path.startsWith("/VAADIN/")
                || path.startsWith("/connect/")) {
            return "vaadin";
        }
        if (path.startsWith("/styles/")
                || path.startsWith("/icons/")
                || path.startsWith("/frontend/")
                || path.startsWith("/webjars/")
                || path.startsWith("/favicon")) {
            return "static";
        }

        return "unmatched";
    }

    private void emit(CanonicalRequestContext context, HttpServletRequest request, HttpServletResponse response) {
        if (!context.markEmitted()) {
            return;
        }

        String previousTraceId = MDC.get("traceId");
        String previousSpanId = MDC.get("spanId");
        try {
            MDC.put("traceId", context.traceId());
            MDC.put("spanId", context.spanId());

            int status = context.forcedServerError() && response.getStatus() < 500
                    ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    : response.getStatus();
            String outcome = status >= 500 ? "server_error" : status >= 400 ? "client_error" : "success";
            Level level = status >= 500 ? Level.ERROR : status >= 400 ? Level.WARN : Level.INFO;
            var event = LOGGER.atLevel(level)
                    .addKeyValue("event", "http.request.completed")
                    .addKeyValue("correlationId", context.correlationId())
                    .addKeyValue("user", context.user())
                    .addKeyValue("endpoint", context.endpoint())
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("status", status)
                    .addKeyValue("durationMs", context.durationMs())
                    .addKeyValue("outcome", outcome)
                    .addKeyValue("requestKind", context.requestKind());

            if (context.errorType() != null) {
                event.addKeyValue("errorType", context.errorType());
            }
            if (context.authenticationFailure() != null) {
                event.addKeyValue("authenticationFailure", context.authenticationFailure());
            }
            if (context.authorizationFailure()) {
                event.addKeyValue("authorizationFailure", true);
            }

            event.log("http.request.completed");
        } catch (RuntimeException ignored) {
            // Observability must not change the HTTP response.
        } finally {
            restoreMdc("traceId", previousTraceId);
            restoreMdc("spanId", previousSpanId);
        }
    }

    private void restoreMdc(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
