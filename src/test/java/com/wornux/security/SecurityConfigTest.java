package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import com.wornux.api.security.ApiAccessDeniedHandler;
import com.wornux.api.security.ApiAuthenticationEntryPoint;
import com.wornux.observability.CanonicalRequestFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

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
        var vaadinConfigurer = mock(VaadinSecurityConfigurer.class, Answers.RETURNS_SELF);
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
        when(http.with(any(), any())).thenAnswer(invocation -> customize(invocation, vaadinConfigurer, http));
        when(http.build()).thenReturn(apiChain);

        var config = new SecurityConfig();
        var jwtConverter = mock(AppJwtAuthenticationConverter.class);
        var authenticationEntryPoint = mock(ApiAuthenticationEntryPoint.class);
        var accessDeniedHandler = mock(ApiAccessDeniedHandler.class);
        var oidcUserService = mock(AppOidcUserService.class);
        var canonicalRequestFilter = mock(CanonicalRequestFilter.class);
        var authenticationFailureCounter = mock(Counter.class);
        var logoutSuccessHandler = mock(LogoutSuccessHandler.class);

        assertThat(config.securityFilterChainApi(
                        http, jwtConverter, authenticationEntryPoint, accessDeniedHandler, canonicalRequestFilter))
                .isSameAs(apiChain);
        assertThat(config.securityFilterChain(
                        http,
                        oidcUserService,
                        canonicalRequestFilter,
                        authenticationFailureCounter,
                        logoutSuccessHandler))
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
        verify(resourceServer).authenticationEntryPoint(authenticationEntryPoint);
        verify(jwt).jwtAuthenticationConverter(jwtConverter);
        verify(oauthLogin).loginPage("/login");
        var failureHandler = ArgumentCaptor.forClass(AuthenticationFailureHandler.class);
        verify(oauthLogin).failureHandler(failureHandler.capture());
        verify(userInfo).oidcUserService(oidcUserService);
        verify(vaadinConfigurer).oauth2LoginPage("/oauth2/authorization/keycloak", "{baseUrl}/login");
        verify(vaadinConfigurer).logoutSuccessHandler(logoutSuccessHandler);

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

        var failedRequest = new MockHttpServletRequest();
        var failedResponse = new MockHttpServletResponse();
        var repeatedResponse = new MockHttpServletResponse();
        var failure = new BadCredentialsException("failed");
        new CanonicalRequestFilter(mock(Tracer.class)).doFilter(failedRequest, failedResponse, (request, response) -> {
            failureHandler.getValue().onAuthenticationFailure(failedRequest, failedResponse, failure);
            failureHandler.getValue().onAuthenticationFailure(failedRequest, repeatedResponse, failure);
        });

        verify(authenticationFailureCounter).increment();
        assertThat(failedResponse.getRedirectedUrl()).isEqualTo("/login?error");
        assertThat(repeatedResponse.getRedirectedUrl()).isEqualTo("/login?error");
    }

    @Test
    void oidcLogoutRedirectsToBrowserFacingKeycloakEndpoint() throws Exception {
        var registration = ClientRegistration.withRegistrationId("keycloak")
                .clientId("inventory-management")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("http://keycloak-internal/auth")
                .tokenUri("http://keycloak-internal/token")
                .jwkSetUri("http://keycloak-internal/certs")
                .providerConfigurationMetadata(Map.of("existing", "metadata"))
                .build();
        ClientRegistrationRepository registrations = mock(ClientRegistrationRepository.class);
        when(registrations.findByRegistrationId("keycloak")).thenReturn(registration);
        var handler = new SecurityConfig()
                .oidcLogoutSuccessHandler(
                        registrations, "http://localhost:7777/realms/wornux/protocol/openid-connect/logout");
        var idToken =
                new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60), Map.of("sub", "user-id"));
        var oidcUser = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
        var authentication = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");
        var request = new MockHttpServletRequest("POST", "/logout");
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        var response = new MockHttpServletResponse();

        handler.onLogoutSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:7777/realms/wornux/protocol/openid-connect/logout?")
                .contains("id_token_hint=id-token")
                .contains("post_logout_redirect_uri=http://localhost/login");
        assertThat(registration.getProviderDetails().getConfigurationMetadata())
                .containsEntry("existing", "metadata")
                .doesNotContainKey("end_session_endpoint");
        verify(registrations).findByRegistrationId("keycloak");

        var missingAuthentication = new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "missing");
        var fallbackResponse = new MockHttpServletResponse();
        handler.onLogoutSuccess(request, fallbackResponse, missingAuthentication);

        assertThat(fallbackResponse.getRedirectedUrl()).isEqualTo("/");
        verify(registrations).findByRegistrationId("missing");
    }

    @Test
    void createsAuthenticationCounterAndDisabledServletFilterRegistration() {
        var config = new SecurityConfig();
        var registry = new SimpleMeterRegistry();
        var filter = mock(CanonicalRequestFilter.class);

        Counter counter = config.authenticationFailureCounter(registry);
        var filterRegistration = config.canonicalRequestFilterRegistration(filter);

        assertThat(counter.getId().getName()).isEqualTo("wornux.security.authentication.failures");
        assertThat(counter.getId().getDescription()).isEqualTo("API and OIDC authentication failures");
        assertThat(filterRegistration.getFilter()).isSameAs(filter);
        assertThat(filterRegistration.isEnabled()).isFalse();
    }

    private static Object customize(InvocationOnMock invocation, Object target, Object result) {
        ((Customizer) invocation.getArgument(invocation.getArguments().length - 1)).customize(target);

        return result;
    }
}
