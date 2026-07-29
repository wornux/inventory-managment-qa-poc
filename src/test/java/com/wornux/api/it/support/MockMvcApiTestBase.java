package com.wornux.api.it.support;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.profiles.active=api-test",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/prod",
            "spring.test.mockmvc.print=none",
            "vaadin.launch-browser=false"
        })
@AutoConfigureMockMvc
public abstract class MockMvcApiTestBase extends AbstractApiTestBase<MvcResult> {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    protected ResultActions request(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(ApiTestProtocol.CORRELATION_ID_HEADER, ApiTestProtocol.correlationId())
                .accept(MediaType.APPLICATION_JSON));
    }

    protected ResultActions authenticate(MockHttpServletRequestBuilder request, String token) throws Exception {
        return request(request.header(HttpHeaders.AUTHORIZATION, ApiTestProtocol.bearer(token)));
    }

    protected ResultActions expectSuccess(ResultActions result, int statusCode) throws Exception {
        return result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    protected ResultActions expectFailure(ResultActions result, int statusCode) throws Exception {
        return result.andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    protected String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    protected Map<String, Object> responseBody(MvcResult result) {
        return jsonMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<>() {});
    }

    @Override
    protected long firstCatalogId(String token, String path) throws Exception {
        MvcResult result = expectSuccess(authenticate(get(path), token), 200).andReturn();
        List<?> data = (List<?>) responseBody(result).get("data");

        if (data == null || data.isEmpty() || !(data.getFirst() instanceof Map<?, ?> first)) {
            throw new IllegalStateException("API test target has no active catalog fixture at " + path);
        }

        Object id = first.get("id");
        if (!(id instanceof Number number)) {
            throw new IllegalStateException("API test catalog fixture has no numeric identifier at " + path);
        }

        return number.longValue();
    }

    @Override
    protected MvcResult createProduct(Map<String, Object> product) throws Exception {
        return expectSuccess(
                        authenticate(
                                post(ApiPaths.PRODUCTS)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(product)),
                                TokenProvider.managerToken()),
                        201)
                .andReturn();
    }

    @Override
    protected MvcResult getProduct(String token, long productId) throws Exception {
        return expectSuccess(authenticate(get(ApiPaths.PRODUCT, productId), token), 200).andReturn();
    }

    @Override
    protected void deleteProduct(long productId) throws Exception {
        expectSuccess(authenticate(delete(ApiPaths.PRODUCT, productId), TokenProvider.managerToken()), 200);
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> dataList(MvcResult result) {
        Object data = responseBody(result).get("data");
        if (!(data instanceof List<?>)) {
            throw new IllegalStateException("API response data is not a list.");
        }

        return (List<Map<String, Object>>) data;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> dataObject(MvcResult result) {
        Object data = responseBody(result).get("data");
        if (!(data instanceof Map<?, ?>)) {
            throw new IllegalStateException("API response data is not an object.");
        }

        return (Map<String, Object>) data;
    }

    protected long dataLong(MvcResult result, String field) {
        Object value = dataObject(result).get(field);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("API response data field is not numeric: " + field);
        }

        return number.longValue();
    }
}
