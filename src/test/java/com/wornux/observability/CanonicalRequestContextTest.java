package com.wornux.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CanonicalRequestContextTest {

    @Test
    void lifecycleStoresOneContextAndCountsEachEventOnce() {
        var request = new MockHttpServletRequest();

        assertThat(CanonicalRequestContext.current(request)).isEmpty();
        assertThat(CanonicalRequestContext.countAuthenticationFailure(request)).isTrue();
        CanonicalRequestContext.authenticationFailure(request, "ignored");
        CanonicalRequestContext.authorizationFailure(request);
        CanonicalRequestContext.error(request, new IllegalStateException());

        CanonicalRequestContext context = CanonicalRequestContext.getOrCreate(request, "correlation");
        assertThat(CanonicalRequestContext.getOrCreate(request, "other")).isSameAs(context);
        CanonicalRequestContext.authenticationFailure(request, "bad\ncode");
        CanonicalRequestContext.authorizationFailure(request);
        CanonicalRequestContext.error(request, new IllegalArgumentException());

        assertThat(context.correlationId()).isEqualTo("correlation");
        assertThat(context.authenticationFailure()).isEqualTo("bad_code");
        assertThat(context.authorizationFailure()).isTrue();
        assertThat(context.errorType()).isEqualTo("IllegalArgumentException");
        assertThat(CanonicalRequestContext.countAuthenticationFailure(request)).isTrue();
        assertThat(CanonicalRequestContext.countAuthenticationFailure(request)).isFalse();
        assertThat(context.markAsyncStarted()).isTrue();
        assertThat(context.markAsyncStarted()).isFalse();
        assertThat(context.isAsyncStarted()).isTrue();
        assertThat(context.markEmitted()).isTrue();
        assertThat(context.markEmitted()).isFalse();
        assertThat(context.durationMs()).isNotNegative();
    }

    @Test
    void captureUsesAuthenticatedSubjectsAndCurrentTrace() {
        CanonicalRequestContext context = context();
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace");
        when(traceContext.spanId()).thenReturn("span");
        Jwt jwt = new Jwt("token", null, null, Map.of("alg", "none"), Map.of("sub", "jwt-user"));
        JwtAuthenticationToken jwtAuthentication = mock(JwtAuthenticationToken.class);
        when(jwtAuthentication.isAuthenticated()).thenReturn(true);
        when(jwtAuthentication.getToken()).thenReturn(jwt);

        context.capture(jwtAuthentication, tracer);

        assertThat(context.user()).isEqualTo("jwt-user");
        assertThat(context.traceId()).isEqualTo("trace");
        assertThat(context.spanId()).isEqualTo("span");

        OidcUser oidcUser = mock(OidcUser.class);
        Authentication oidc = mock(Authentication.class);
        when(oidc.isAuthenticated()).thenReturn(true);
        when(oidc.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn("oidc-user");
        context.capture(oidc, mock(Tracer.class));
        assertThat(context.user()).isEqualTo("oidc-user");

        Authentication jwtPrincipal = mock(Authentication.class);
        when(jwtPrincipal.isAuthenticated()).thenReturn(true);
        when(jwtPrincipal.getPrincipal()).thenReturn(jwt);
        context.capture(jwtPrincipal, mock(Tracer.class));
        assertThat(context.user()).isEqualTo("jwt-user");
    }

    @Test
    void captureKeepsAnonymousFallbackForUnavailableSubjects() {
        CanonicalRequestContext context = context();
        Tracer tracer = mock(Tracer.class);
        Authentication unauthenticated = mock(Authentication.class);
        Authentication unknown = mock(Authentication.class);
        when(unknown.isAuthenticated()).thenReturn(true);
        var anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        context.capture(null, tracer);
        context.capture(unauthenticated, tracer);
        context.capture(anonymous, tracer);
        context.capture(unknown, tracer);
        Authentication blankSubject = mock(Authentication.class);
        OidcUser oidcUser = mock(OidcUser.class);
        when(blankSubject.isAuthenticated()).thenReturn(true);
        when(blankSubject.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getSubject()).thenReturn(" ");
        context.capture(blankSubject, tracer);

        assertThat(context.user()).isEqualTo("anonymous");
        assertThat(context.traceId()).isEqualTo("unavailable");
        assertThat(context.spanId()).isEqualTo("unavailable");
    }

    @Test
    void endpointAndErrorsAreBoundedAndPreserveFirstCause() {
        CanonicalRequestContext context = context();

        context.endpoint(" ", null);
        assertThat(context.endpoint()).isEqualTo("unmatched");
        assertThat(context.requestKind()).isEqualTo("unmatched");
        context.endpoint("x".repeat(300), "kind");
        assertThat(context.endpoint()).hasSize(256);
        context.error(null);
        context.escapingError(new IllegalStateException());
        context.error(new IllegalArgumentException());

        assertThat(context.errorType()).isEqualTo("IllegalStateException");
        assertThat(context.forcedServerError()).isTrue();

        CanonicalRequestContext timeout = context();
        timeout.asyncTimeout();
        timeout.asyncTimeout();
        assertThat(timeout.errorType()).isEqualTo("AsyncTimeout");
        assertThat(timeout.forcedServerError()).isTrue();
    }

    private static CanonicalRequestContext context() {
        return CanonicalRequestContext.getOrCreate(new MockHttpServletRequest(), "id");
    }
}
