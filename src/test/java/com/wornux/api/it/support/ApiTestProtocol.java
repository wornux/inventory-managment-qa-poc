package com.wornux.api.it.support;

import java.util.UUID;

public final class ApiTestProtocol {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    private ApiTestProtocol() {}

    public static String correlationId() {
        return UUID.randomUUID().toString();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
