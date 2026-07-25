package com.wornux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

class ApplicationTest {

    @Test
    void mainStartsThisApplicationWithTheProvidedArguments() {
        assertThat(new Application()).isNotNull();
        try (var springApplication = mockStatic(SpringApplication.class)) {
            Application.main("--spring.main.web-application-type=none");
            springApplication.verify(() -> SpringApplication.run(
                    Application.class, "--spring.main.web-application-type=none"));
        }
    }
}
