package com.wornux.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.testbench.BrowserTest;
import com.wornux.e2e.page.ProductDraft;
import com.wornux.e2e.page.ProductsPage;
import com.wornux.e2e.support.AbstractInventoryIT;
import com.wornux.e2e.support.E2eFixtures;

class ProductLifecycleIT extends AbstractInventoryIT {

    @BrowserTest
    void administrator_can_create_find_edit_and_delete_a_product() {
        var application = signInAs(SYSTEM_ADMINISTRATOR);
        application.navigateTo("products");
        var products = new ProductsPage(this);
        var product =
                ProductDraft.lowStock("E2E-LIFECYCLE", "Lifecycle Widget", E2eFixtures.CATEGORY, E2eFixtures.SUPPLIER);

        products.create(product);
        assertThat(products.shows(product.sku())).isTrue();

        products.edit(product.sku(), product.renamed("Lifecycle Widget Updated"));
        assertThat(products.shows("Lifecycle Widget Updated")).isTrue();

        products.showOnlyLowStock();
        assertThat(products.shows(product.sku())).isTrue();

        products.delete(product.sku());
        assertThat(products.shows(product.sku())).isFalse();
    }

    @BrowserTest
    void product_form_explains_which_required_values_are_missing() {
        var application = signInAs(SYSTEM_ADMINISTRATOR);
        application.navigateTo("products");
        var products = new ProductsPage(this);

        products.submitBlankProduct();

        assertThat(products.requiredFieldsAreInvalid()).isTrue();
    }

    @BrowserTest
    void duplicate_sku_is_rejected_without_creating_another_product() {
        String sku = "E2E-DUPLICATE";
        givenProduct(sku, "Existing Product", 10, 5);
        var application = signInAs(SYSTEM_ADMINISTRATOR);
        application.navigateTo("products");
        var products = new ProductsPage(this);
        var duplicate = ProductDraft.lowStock(sku, "Duplicate Product", E2eFixtures.CATEGORY, E2eFixtures.SUPPLIER);

        products.attemptToCreate(duplicate);

        assertThat(products.errorMessage()).contains("SKU already exists");
        assertThat(products.formIsOpen()).isTrue();

        open("/products");
        products = new ProductsPage(this).searchFor(sku);
        assertThat(products.matchingProductCount(sku)).isEqualTo(1);
    }
}
