package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakAdminClientTest {
    private MockRestServiceServer server;
    private KeycloakAdminClient client;
    private final KeycloakAdminBootstrapProperties properties = KeycloakAdminBootstrapTest.properties(true);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(builder);
    }

    @Test
    void obtainsPasswordGrantAdminToken() {
        server.expect(requestTo("http://keycloak:7777/realms/master/protocol/openid-connect/token"))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content()
                        .string(allOf(
                                containsString("grant_type=password"),
                                containsString("client_id=admin-cli"),
                                containsString("username=admin"),
                                containsString("password=secret"))))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));

        assertThat(client.adminToken(properties)).isEqualTo("token");
        server.verify();
    }

    @Test
    void rejectsMissingBlankAndNullTokenResponses() {
        for (String body : new String[] {"{}", "{\"access_token\":\"  \"}", "null"}) {
            setUp();
            server.expect(requestTo("http://keycloak:7777/realms/master/protocol/openid-connect/token"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> client.adminToken(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("access_token");
            server.verify();
        }
    }

    @Test
    void createsApplicationUserWithPermanentPassword() {
        server.expect(requestTo("http://keycloak:7777/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak:7777/admin/realms/app/users"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content()
                        .json("{\"username\":\"new-user\",\"email\":\"new@example.com\","
                                + "\"firstName\":\"new-user\",\"lastName\":\"User\","
                                + "\"emailVerified\":true,\"enabled\":true,\"requiredActions\":[],"
                                + "\"credentials\":[{\"type\":\"password\",\"value\":\"password1\","
                                + "\"temporary\":false}]}"))
                .andRespond(withCreatedEntity(URI.create("http://keycloak:7777/admin/realms/app/users/2")));

        assertThat(client.createUser(properties, "new-user", "new@example.com", "password1").id()).isEqualTo("2");
        server.verify();
    }

    @Test
    void reportsMissingCreatedUserIdentifier() {
        server.expect(requestTo("http://keycloak:7777/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak:7777/admin/realms/app/users")).andRespond(withNoContent());

        assertThatThrownBy(() -> client.createUser(properties, "new-user", "new@example.com", "password1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Keycloak user response did not include its identifier.");
        server.verify();
    }

    @Test
    void reportsTrailingSlashCreatedUserIdentifier() {
        server.expect(requestTo("http://keycloak:7777/realms/master/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak:7777/admin/realms/app/users"))
                .andRespond(withCreatedEntity(URI.create("http://keycloak:7777/admin/realms/app/users/")));

        assertThatThrownBy(() -> client.createUser(properties, "new-user", "new@example.com", "password1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Keycloak user response did not include its identifier.");
        server.verify();
    }

    @Test
    void returnsExistingUserWithoutCreatingOne() {
        expectFind("[{\"id\":\"1\",\"username\":\"sysadmin\",\"email\":\"sys@example.com\"}]");

        assertThat(client.ensureUser(properties, "token"))
                .isEqualTo(new KeycloakAdminClient.KeycloakUser("1", "sysadmin", "sys@example.com"));
        server.verify();
    }

    @Test
    void createsMissingUserThenReturnsIt() {
        expectFind("[]");
        server.expect(requestTo("http://keycloak:7777/admin/realms/app/users"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content()
                        .json("{\"username\":\"sysadmin\",\"email\":\"sys@example.com\","
                                + "\"firstName\":\"System\",\"lastName\":\"Administrator\",\"emailVerified\":true,"
                                + "\"enabled\":true,\"requiredActions\":[],\"credentials\":[{\"type\":\"password\","
                                + "\"value\":\"user-secret\",\"temporary\":false}]}"))
                .andRespond(withNoContent());
        expectFind("[{\"id\":\"1\",\"username\":\"sysadmin\",\"email\":\"sys@example.com\"}]");

        assertThat(client.ensureUser(properties, "token").id()).isEqualTo("1");
        server.verify();
    }

    @Test
    void reportsWhenCreateDoesNotBecomeVisibleAndWhenUserFieldsAreInvalid() {
        expectFind("[]");
        server.expect(requestTo("http://keycloak:7777/admin/realms/app/users")).andRespond(withNoContent());
        expectFind("null");

        assertThatThrownBy(() -> client.ensureUser(properties, "token"))
                .hasMessage("Keycloak user was not created.");
        server.verify();

        setUp();
        expectFind("[{\"id\":\"1\",\"username\":\"sysadmin\",\"email\":\"\"}]");

        assertThatThrownBy(() -> client.ensureUser(properties, "token")).hasMessage("Keycloak user is missing email.");
        server.verify();

        setUp();
        expectFind("[{\"id\":1,\"username\":\"sysadmin\",\"email\":\"sys@example.com\"}]");

        assertThatThrownBy(() -> client.ensureUser(properties, "token")).hasMessage("Keycloak user is missing id.");
        server.verify();
    }

    private void expectFind(String response) {
        server.expect(requestTo("http://keycloak:7777/admin/realms/app/users?email=sys@example.com&exact=true"))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }
}
