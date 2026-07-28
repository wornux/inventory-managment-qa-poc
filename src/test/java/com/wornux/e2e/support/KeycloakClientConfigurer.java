package com.wornux.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

final class KeycloakClientConfigurer {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private KeycloakClientConfigurer() {}

    static void allowApplicationCallback(String applicationUrl) {
        try {
            String token = adminToken();
            ObjectNode client = inventoryClient(token);
            client.put("rootUrl", applicationUrl);
            client.put("adminUrl", applicationUrl);
            client.put("baseUrl", applicationUrl + "/");
            client.set("redirectUris", array(applicationUrl + "/login/oauth2/code/keycloak"));
            client.set("webOrigins", array(applicationUrl));
            ((ObjectNode) client.withObject("/attributes")).put("post.logout.redirect.uris", applicationUrl + "/login");

            send(HttpRequest.newBuilder()
                    .uri(URI.create(E2eEnvironment.keycloakUrl()
                            + "/admin/realms/wornux/clients/"
                            + client.required("id").asText()))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(client)))
                    .build());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not configure the Keycloak callback for E2E tests.", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not configure the Keycloak callback for E2E tests.", exception);
        }
    }

    private static String adminToken() throws IOException, InterruptedException {
        String form = "client_id=admin-cli&grant_type=password&username=admin&password="
                + URLEncoder.encode(E2eEnvironment.ADMIN_PASSWORD, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(E2eEnvironment.keycloakUrl() + "/realms/master/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build());

        return JSON.readTree(response.body()).required("access_token").asText();
    }

    private static ObjectNode inventoryClient(String token) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(
                        E2eEnvironment.keycloakUrl() + "/admin/realms/wornux/clients?clientId=inventory-management"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build());
        JsonNode clients = JSON.readTree(response.body());

        if (!clients.isArray() || clients.isEmpty()) {
            throw new IllegalStateException("The inventory-management Keycloak client was not imported.");
        }

        return (ObjectNode) clients.get(0);
    }

    private static ArrayNode array(String value) {
        return JSON.createArrayNode().add(value);
    }

    private static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Keycloak returned HTTP " + response.statusCode() + ": " + response.body());
        }

        return response;
    }
}
