package com.wornux.security.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppPermissionTest {

    @Test
    void metadataAndLookupAreStable() {
        assertThat(AppResource.PRODUCT.code()).isEqualTo("product");
        assertThat(AppResource.PRODUCT.label()).isEqualTo("Product");
        assertThat(AppAction.UPDATE.code()).isEqualTo("update");
        assertThat(AppAction.UPDATE.label()).isEqualTo("Update");
        assertThat(AppPermission.PRODUCT_UPDATE.resource()).isEqualTo(AppResource.PRODUCT);
        assertThat(AppPermission.PRODUCT_UPDATE.action()).isEqualTo(AppAction.UPDATE);
        assertThat(AppPermission.PRODUCT_UPDATE.code()).isEqualTo("product:update");
        assertThat(AppPermission.PRODUCT_UPDATE.label()).isEqualTo("Product · Update");
        assertThat(AppPermission.fromCode("PrOdUcT:UpDaTe")).contains(AppPermission.PRODUCT_UPDATE);
        assertThat(AppPermission.fromCode("role:assign")).contains(AppPermission.ROLE_ASSIGN);
        assertThat(AppPermission.fromCode("user:assign")).isEmpty();
        assertThat(AppPermission.fromCode("unknown")).isEmpty();
    }

    @Test
    void mutationsGrantViewOnSameResourceOnly() {
        assertThat(AppAction.UPDATE.grants(AppAction.UPDATE)).isTrue();
        assertThat(AppAction.UPDATE.grants(AppAction.VIEW)).isTrue();
        assertThat(AppAction.VIEW.grants(AppAction.UPDATE)).isFalse();
        assertThat(AppPermission.PRODUCT_UPDATE.grants(AppPermission.PRODUCT_VIEW))
                .isTrue();
        assertThat(AppPermission.PRODUCT_UPDATE.grants(AppPermission.PRODUCT_DELETE))
                .isFalse();
        assertThat(AppPermission.PRODUCT_UPDATE.grants(AppPermission.CATEGORY_VIEW))
                .isFalse();
    }
}
