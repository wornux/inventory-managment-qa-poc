package com.wornux.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void allowedOrigins_nullValue_becomesEmptyList() {
        var properties = new CorsProperties(null);

        assertThat(properties.allowedOrigins()).isEmpty();
    }

    @Test
    void allowedOrigins_configuredValues_areTrimmedAndBlankValuesRemoved() {
        var properties = new CorsProperties(List.of(" https://frontend-a.test ", "", "  ", "https://frontend-b.test"));

        assertThat(properties.allowedOrigins()).containsExactly("https://frontend-a.test", "https://frontend-b.test");
    }
}
