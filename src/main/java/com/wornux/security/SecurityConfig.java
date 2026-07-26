package com.wornux.security;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import com.wornux.api.security.ApiAccessDeniedHandler;
import com.wornux.api.security.ApiAuthenticationEntryPoint;
import com.wornux.observability.CanonicalRequestContext;
import com.wornux.observability.CanonicalRequestFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] OPEN_API_ENDPOINTS = {
        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/webjars/swagger-ui/**"
    };

    @Bean
    Counter authenticationFailureCounter(MeterRegistry meterRegistry) {
        return Counter.builder("wornux.security.authentication.failures")
                .description("API and OIDC authentication failures")
                .register(meterRegistry);
    }

    @Bean
    FilterRegistrationBean<CanonicalRequestFilter> canonicalRequestFilterRegistration(
            CanonicalRequestFilter canonicalRequestFilter) {
        var registration = new FilterRegistrationBean<>(canonicalRequestFilter);
        registration.setEnabled(false);

        return registration;
    }

    @Bean
    @Order(1)
    SecurityFilterChain securityFilterChainApi(
            HttpSecurity http,
            AppJwtAuthenticationConverter jwtAuthenticationConverter,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            ApiAccessDeniedHandler apiAccessDeniedHandler,
            CanonicalRequestFilter canonicalRequestFilter)
            throws Exception {
        http.securityMatcher(
                        "/api/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/webjars/swagger-ui/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(OPEN_API_ENDPOINTS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(canonicalRequestFilter, SecurityContextHolderFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppOidcUserService oidcUserService,
            CanonicalRequestFilter canonicalRequestFilter,
            Counter authenticationFailureCounter)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/styles/**", "/icons/**", "/actuator/health", "/actuator/prometheus")
                .permitAll());

        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
            configurer.oauth2LoginPage("/oauth2/authorization/keycloak", "{baseUrl}/login");
        });

        http.oauth2Login(oauth2 -> oauth2.loginPage("/login")
                .failureHandler((request, response, exception) -> {
                    CanonicalRequestContext.authenticationFailure(request, "oidc_failure");
                    if (CanonicalRequestContext.countAuthenticationFailure(request)) {
                        authenticationFailureCounter.increment();
                    }
                    response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/login?error"));
                })
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)));
        http.addFilterAfter(canonicalRequestFilter, SecurityContextHolderFilter.class);

        return http.build();
    }
}
