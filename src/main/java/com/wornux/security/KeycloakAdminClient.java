package com.wornux.security;

import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;

@Component
public class KeycloakAdminClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public KeycloakAdminClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public String adminToken(KeycloakAdminBootstrapProperties properties) {
        var body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "password");
        body.add("client_id", properties.adminClientId());
        body.add("username", properties.adminUsername());
        body.add("password", properties.adminPassword());

        Map<String, Object> response = restClient.post()
                .uri(properties.baseUrl() + "/realms/" + properties.adminRealm()
                        + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(MAP_TYPE);
        Object token = response == null ? null : response.get("access_token");
        if (token instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new IllegalStateException("Keycloak admin token response did not include access_token.");
    }

    public KeycloakUser ensureUser(KeycloakAdminBootstrapProperties properties, String token) {
        return findUser(properties, token)
                .stream()
                .findFirst()
                .map(KeycloakUser::from)
                .orElseGet(() -> {
                    createUser(properties, token);
                    return findUser(properties, token)
                            .stream()
                            .findFirst()
                            .map(KeycloakUser::from)
                            .orElseThrow(() -> new IllegalStateException("Keycloak admin user was not created."));
                });
    }

    private List<Map<String, Object>> findUser(KeycloakAdminBootstrapProperties properties, String token) {
        List<Map<String, Object>> response = restClient.get()
                .uri(UriComponentsBuilder.fromUriString(properties.baseUrl())
                        .path("/admin/realms/{realm}/users")
                        .queryParam("email", properties.userEmail())
                        .queryParam("exact", "true")
                        .build(properties.realm()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(LIST_TYPE);
        return response == null ? List.of() : response;
    }

    private void createUser(KeycloakAdminBootstrapProperties properties, String token) {
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", properties.userPassword(),
                "temporary", false);
        Map<String, Object> body = Map.of(
                "username", properties.userUsername(),
                "email", properties.userEmail(),
                "firstName", "System",
                "lastName", "Administrator",
                "emailVerified", true,
                "enabled", true,
                "requiredActions", List.of(),
                "credentials", List.of(credential));
        restClient.post()
                .uri(properties.baseUrl() + "/admin/realms/" + properties.realm() + "/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public record KeycloakUser(String id, String username, String email) {

        static KeycloakUser from(Map<String, Object> value) {
            return new KeycloakUser(
                    string(value, "id"),
                    string(value, "username"),
                    string(value, "email"));
        }

        private static String string(Map<String, Object> value, String key) {
            Object result = value.get(key);
            if (result instanceof String string && !string.isBlank()) {
                return string;
            }
            throw new IllegalStateException("Keycloak user is missing " + key + ".");
        }
    }
}
