package com.wornux.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JacksonConfig {

    @Bean
    JsonMapper jsonMapper(JsonMapper.Builder builder) {
        return builder.build();
    }
}
