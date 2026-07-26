package com.wornux.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.wornux.api.ApiResponse;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ApiSecurityWritersTest {
    private final JsonMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    @Test
    void deniedHandlerWritesAForbiddenJsonEnvelope() throws Exception {
        var response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler(mapper)
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("missing"));
        ApiResponse<Object> body = mapper.readValue(response.getContentAsByteArray(), new TypeReference<>() {});

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(body.success()).isFalse();
        assertThat(body.message()).isEqualTo("Access denied.");
        assertThat(body.errors().getFirst().message()).isEqualTo("Permission is required.");
    }

    @Test
    void entryPointWritesStableUnauthorizedMessageWithoutLeakingException() throws Exception {
        var response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint(mapper, mock(Counter.class))
                .commence(new MockHttpServletRequest(), response, new BadCredentialsException("secret"));
        ApiResponse<Object> body = mapper.readValue(response.getContentAsByteArray(), new TypeReference<>() {});

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.message()).isEqualTo("Authentication failed.");
        assertThat(body.errors().getFirst().message()).isEqualTo("A valid bearer token is required.");
    }
}
