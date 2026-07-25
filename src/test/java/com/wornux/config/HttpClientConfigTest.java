package com.wornux.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpClientConfigTest {
    @Test
    void createsIndependentUsableRestClientBuilders() {
        var config = new HttpClientConfig();

        assertThat(config.restClientBuilder()).isNotNull().isNotSameAs(config.restClientBuilder());
        assertThat(config.restClientBuilder().build()).isNotNull();
    }
}
