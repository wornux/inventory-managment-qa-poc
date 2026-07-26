package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.api.security.ApiAccessDeniedHandler;
import com.wornux.api.security.ApiAuthenticationEntryPoint;
import com.wornux.observability.CanonicalRequestFilter;
import io.micrometer.core.instrument.Counter;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.DefaultSecurityFilterChain;

class SecurityConfigTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void configuresBothFilterChains() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        DefaultSecurityFilterChain apiChain = mock(DefaultSecurityFilterChain.class);
        var csrf = mock(CsrfConfigurer.class);
        var sessions = mock(SessionManagementConfigurer.class);
        var exceptions = mock(ExceptionHandlingConfigurer.class, Answers.RETURNS_SELF);
        var authorizationRegistries =
                new ArrayList<AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry>();
        var permittedRequests = new ArrayList<AuthorizeHttpRequestsConfigurer.AuthorizedUrl>();
        var authenticatedRequests = new ArrayList<AuthorizeHttpRequestsConfigurer.AuthorizedUrl>();
        var resourceServer = mock(OAuth2ResourceServerConfigurer.class, Answers.RETURNS_SELF);
        var jwt = mock(OAuth2ResourceServerConfigurer.JwtConfigurer.class, Answers.RETURNS_SELF);
        var oauthLogin = mock(OAuth2LoginConfigurer.class, Answers.RETURNS_SELF);
        var userInfo = mock(OAuth2LoginConfigurer.UserInfoEndpointConfig.class, Answers.RETURNS_SELF);
        when(http.securityMatcher(any(String[].class))).thenReturn(http);
        when(http.csrf(any())).thenAnswer(invocation -> customize(invocation, csrf, http));
        when(http.sessionManagement(any())).thenAnswer(invocation -> customize(invocation, sessions, http));
        when(http.exceptionHandling(any())).thenAnswer(invocation -> customize(invocation, exceptions, http));
        when(http.authorizeHttpRequests(any())).thenAnswer(invocation -> {
            var registry = mock(
                    AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry.class,
                    Answers.RETURNS_SELF);
            var permitted = mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
            var authenticated = mock(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class);
            when(registry.requestMatchers(any(String[].class))).thenReturn(permitted);
            when(registry.anyRequest()).thenReturn(authenticated);
            when(permitted.permitAll()).thenReturn(registry);
            when(authenticated.authenticated()).thenReturn(registry);

            authorizationRegistries.add(registry);
            permittedRequests.add(permitted);
            authenticatedRequests.add(authenticated);

            return customize(invocation, registry, http);
        });
        when(http.oauth2ResourceServer(any())).thenAnswer(invocation -> {
            when(resourceServer.jwt(any(Customizer.class))).thenAnswer(jwtInvocation -> {
                return customize(jwtInvocation, jwt, resourceServer);
            });

            return customize(invocation, resourceServer, http);
        });
        when(http.oauth2Login(any())).thenAnswer(invocation -> {
            when(oauthLogin.userInfoEndpoint(any(Customizer.class))).thenAnswer(userInfoInvocation -> {
                return customize(userInfoInvocation, userInfo, oauthLogin);
            });

            return customize(invocation, oauthLogin, http);
        });
        when(http.addFilterAfter(any(), any())).thenReturn(http);
        when(http.with(any(), any())).thenAnswer(invocation -> customize(invocation, invocation.getArgument(0), http));
        when(http.build()).thenReturn(apiChain);

        var config = new SecurityConfig();
        var jwtConverter = mock(AppJwtAuthenticationConverter.class);
        var authenticationEntryPoint = mock(ApiAuthenticationEntryPoint.class);
        var accessDeniedHandler = mock(ApiAccessDeniedHandler.class);
        var oidcUserService = mock(AppOidcUserService.class);
        var canonicalRequestFilter = mock(CanonicalRequestFilter.class);
        var authenticationFailureCounter = mock(Counter.class);

        assertThat(config.securityFilterChainApi(
                        http,
                        jwtConverter,
                        authenticationEntryPoint,
                        accessDeniedHandler,
                        canonicalRequestFilter))
                .isSameAs(apiChain);
        assertThat(config.securityFilterChain(
                        http, oidcUserService, canonicalRequestFilter, authenticationFailureCounter))
                .isSameAs(apiChain);

        verify(http)
                .securityMatcher(
                        "/api/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/webjars/swagger-ui/**");
        verify(csrf).disable();
        verify(sessions).sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        verify(exceptions).authenticationEntryPoint(authenticationEntryPoint);
        verify(exceptions).accessDeniedHandler(accessDeniedHandler);
        verify(jwt).jwtAuthenticationConverter(jwtConverter);
        verify(oauthLogin).loginPage("/login");
        verify(oauthLogin).failureHandler(any());
        verify(userInfo).oidcUserService(oidcUserService);

        var apiAuthorization = authorizationRegistries.get(0);

        verify(apiAuthorization)
                .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/webjars/swagger-ui/**");
        verify(permittedRequests.get(0)).permitAll();
        verify(authenticatedRequests.get(0)).authenticated();

        var browserAuthorization = authorizationRegistries.get(1);

        verify(browserAuthorization)
                .requestMatchers("/styles/**", "/icons/**", "/actuator/health", "/actuator/prometheus");
        verify(permittedRequests.get(1)).permitAll();
    }

    private static Object customize(InvocationOnMock invocation, Object target, Object result) {
        ((Customizer) invocation.getArgument(invocation.getArguments().length - 1)).customize(target);

        return result;
    }
}
