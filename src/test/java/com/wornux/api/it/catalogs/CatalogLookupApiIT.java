package com.wornux.api.it.catalogs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wornux.api.it.support.ApiPaths;
import com.wornux.api.it.support.MockMvcApiTestBase;
import com.wornux.api.it.support.TokenProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class CatalogLookupApiIT extends MockMvcApiTestBase {

    @Test
    void categories_supportDefaultAndTextFiltering() throws Exception {
        String token = TokenProvider.viewerToken();
        MvcResult categoriesResult = expectSuccess(authenticate(get(ApiPaths.CATEGORIES), token), 200)
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();
        var categories = dataList(categoriesResult);

        assertThat(categories).isNotEmpty().allSatisfy(category -> assertThat(category).containsKeys("id", "name"));
        String name = (String) categories.getFirst().get("name");
        String search = name.substring(0, Math.min(4, name.length()));

        MvcResult filteredResult = expectSuccess(
                        authenticate(get(ApiPaths.CATEGORIES).queryParam("text", search).queryParam("active", "true"), token),
                        200)
                .andReturn();

        assertThat(dataList(filteredResult))
                .extracting(category -> (String) category.get("name"))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(value).containsIgnoringCase(search));
    }

    @Test
    void suppliers_supportActiveFilterAndEmptyResults() throws Exception {
        MvcResult result = expectSuccess(
                        authenticate(get(ApiPaths.SUPPLIERS)
                                        .queryParam("text", "api-test-name-that-does-not-exist")
                                        .queryParam("active", "false"),
                                TokenProvider.viewerToken()),
                        200)
                .andReturn();

        assertThat(dataList(result)).allSatisfy(value -> assertThat(value).isInstanceOf(Map.class));
    }
}
