package com.wornux.security;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import com.wornux.api.security.ApiAccessDeniedHandler;
import com.wornux.api.security.ApiAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] OPEN_API_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/webjars/swagger-ui/**"
    };

    @Bean
    @Order(1)
    SecurityFilterChain securityFilterChainApi(
            HttpSecurity http,
            AppJwtAuthenticationConverter jwtAuthenticationConverter,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            ApiAccessDeniedHandler apiAccessDeniedHandler) throws Exception {
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
                        .requestMatchers(OPEN_API_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppOidcUserService oidcUserService) throws Exception {

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/styles/**", "/icons/**").permitAll());

        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
            configurer.oauth2LoginPage("/oauth2/authorization/keycloak", "{baseUrl}/login");
        });

        http.oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .failureUrl("/login?error")
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
        );

        return http.build();
    }
}
