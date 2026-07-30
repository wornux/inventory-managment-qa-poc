package com.wornux.api.it.support;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

public final class TokenProvider {

    private static final ParameterizedTypeReference<Map<String, Object>> TOKEN_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final Map<Actor, CachedToken> TOKENS = new EnumMap<>(Actor.class);

    private TokenProvider() {}

    public static synchronized String managerToken() {
        return token(Actor.MANAGER);
    }

    public static synchronized String viewerToken() {
        return token(Actor.VIEWER);
    }

    private static String token(Actor actor) {
        CachedToken cached = TOKENS.get(actor);
        if (cached != null && cached.refreshAfter().isAfter(Instant.now())) {
            return cached.value();
        }

        return loadToken(actor);
    }

    private static String loadToken(Actor actor) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", ApiTestEnvironment.CLIENT);
        form.add("grant_type", "password");
        form.add("scope", "openid profile email");
        form.add("username", actor.username());
        form.add("password", actor.password());

        Map<String, Object> response = RestClient.create()
                .post()
                .uri(ApiTestEnvironment.issuerUri() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TOKEN_RESPONSE_TYPE);
        Object accessToken = response == null ? null : response.get("access_token");
        Object expiresIn = response == null ? null : response.get("expires_in");

        if (!(accessToken instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("Keycloak token response did not include an access token.");
        }

        long lifetimeSeconds = expiresIn instanceof Number number ? number.longValue() : 60;
        TOKENS.put(actor, new CachedToken(value, Instant.now().plusSeconds(Math.max(1, lifetimeSeconds - 30))));

        return value;
    }

    private record CachedToken(String value, Instant refreshAfter) {}

    private enum Actor {
        MANAGER(ApiTestEnvironment.MANAGER_USERNAME, ApiTestEnvironment.MANAGER_PASSWORD),
        VIEWER(ApiTestEnvironment.VIEWER_USERNAME, ApiTestEnvironment.VIEWER_PASSWORD);

        private final String username;
        private final String password;

        Actor(String username, String password) {
            this.username = username;
            this.password = password;
        }

        String username() {
            return username;
        }

        String password() {
            return password;
        }
    }
}
