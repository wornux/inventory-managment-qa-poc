package com.wornux.api.it.support;

import static io.restassured.config.LogConfig.logConfig;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.springframework.http.HttpHeaders;

public final class RestAssuredApiSpecifications {

    private static volatile String baseUrl;

    static {
        RestAssured.config = RestAssuredConfig.config().logConfig(logConfig().blacklistHeader("Authorization"));
    }

    private RestAssuredApiSpecifications() {}

    public static void useBaseUrl(String value) {
        baseUrl = value;
    }

    public static RequestSpecification request() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("API test base URL has not been initialized.");
        }

        return new RequestSpecBuilder()
                .setBaseUri(baseUrl)
                .setAccept(ContentType.JSON)
                .setContentType(ContentType.JSON)
                .addHeader(ApiTestProtocol.CORRELATION_ID_HEADER, ApiTestProtocol.correlationId())
                .build();
    }

    public static RequestSpecification authenticate(String token) {
        return new RequestSpecBuilder()
                .addRequestSpecification(request())
                .addHeader(HttpHeaders.AUTHORIZATION, ApiTestProtocol.bearer(token))
                .build();
    }

    public static ResponseSpecification expectSuccess(int statusCode) {
        return new ResponseSpecBuilder()
                .expectStatusCode(statusCode)
                .expectContentType(ContentType.JSON)
                .expectBody("success", equalTo(true))
                .expectBody("message", not(anyOf(nullValue(), emptyString())))
                .expectBody("errors", hasSize(0))
                .build();
    }

    public static ResponseSpecification expectFailure(int statusCode) {
        return new ResponseSpecBuilder()
                .expectStatusCode(statusCode)
                .expectContentType(ContentType.JSON)
                .expectBody("success", equalTo(false))
                .expectBody("data", equalTo(null))
                .expectBody("errors", not(empty()))
                .build();
    }
}
