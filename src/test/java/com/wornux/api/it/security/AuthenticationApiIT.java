package com.wornux.api.it.security;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

class AuthenticationApiIT extends MockMvcApiTestBase {

    @ParameterizedTest
    @ValueSource(
            strings = {
                ApiPaths.PRODUCTS,
                ApiPaths.STOCK_MOVEMENTS,
                ApiPaths.CATEGORIES,
                ApiPaths.SUPPLIERS,
                ApiPaths.CURRENT_USER_PERMISSIONS
            })
    void missingBearerToken_returnsUnauthorizedForEveryApiModule(String path) throws Exception {
        expectFailure(request(get(path)), 401);
    }

    @Test
    void malformedBearerToken_returnsUnauthorized() throws Exception {
        expectFailure(request(get(ApiPaths.PRODUCTS).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")), 401);
    }

    @Test
    void authenticatedViewer_receivesOnlyOwnEffectivePermissions() throws Exception {
        expectSuccess(
                        authenticate(get(ApiPaths.CURRENT_USER_PERMISSIONS), TokenProvider.viewerToken()),
                        200)
                .andExpect(jsonPath("$.data.permissions", hasItem("product:view")))
                .andExpect(jsonPath("$.data.permissions", not(hasItem("product:create"))));
    }
}
