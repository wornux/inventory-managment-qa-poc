package com.wornux.api.it.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

class CorsApiIT extends MockMvcApiTestBase {

    @Test
    void preflight_allowedOriginAndMethod_returnsCorsAuthorization() throws Exception {
        mockMvc.perform(options(ApiPaths.PRODUCTS)
                        .header(HttpHeaders.ORIGIN, "https://trusted-client.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://trusted-client.test"))
                .andExpect(header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(HttpMethod.POST.name())))
                .andExpect(header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                                containsStringIgnoringCase(HttpHeaders.AUTHORIZATION)));
    }

    @Test
    void preflight_unknownOrigin_isRejectedWithoutCorsAuthorization() throws Exception {
        mockMvc.perform(options(ApiPaths.PRODUCTS)
                        .header(HttpHeaders.ORIGIN, "https://unknown-client.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void preflight_disallowedMethod_isRejectedWithoutCorsAuthorization() throws Exception {
        mockMvc.perform(options(ApiPaths.PRODUCTS)
                        .header(HttpHeaders.ORIGIN, "https://trusted-client.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.TRACE.name()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void httpClient_withoutOrigin_remainsAuthorizedByJwt() throws Exception {
        expectSuccess(authenticate(get(ApiPaths.PRODUCTS), TokenProvider.viewerToken()), 200);
    }
}
