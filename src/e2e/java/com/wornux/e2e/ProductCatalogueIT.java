package com.wornux.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.testbench.BrowserTest;
import com.wornux.e2e.page.ProductsPage;
import com.wornux.e2e.support.AbstractInventoryIT;
import com.wornux.e2e.support.E2eFixtures;

class ProductCatalogueIT extends AbstractInventoryIT {

    @BrowserTest
    void catalogue_search_and_filters_return_only_matching_products() {
        givenProduct("E2E-LOW", "Low Stock Product", 2, 5);
        givenProduct("E2E-HEALTHY", "Healthy Product", 20, 5);
        var application = signInAs(INVENTORY_VIEWER);
        application.navigateTo("products");
        var products = new ProductsPage(this);

        products.searchFor("E2E-LOW");
        assertThat(products.shows("E2E-LOW")).isTrue();
        assertThat(products.shows("E2E-HEALTHY")).isFalse();

        products.searchFor("").filterByCategory(E2eFixtures.CATEGORY).showOnlyLowStock();
        assertThat(products.shows("E2E-LOW")).isTrue();
        assertThat(products.shows("E2E-HEALTHY")).isFalse();
    }

    @BrowserTest
    void catalogue_sorting_and_lazy_paging_reach_the_complete_result_set() {
        givenNumberedProducts(55);
        var application = signInAs(INVENTORY_VIEWER);
        application.navigateTo("products");
        var products = new ProductsPage(this);

        products.loadCompleteResultSet();
        assertThat(products.productCount()).isEqualTo(55);

        products.scrollToProductRow(49);
        assertThat(products.rowText(49)).contains("E2E-PAGE-049");

        products.sortProductsDescending();
        assertThat(products.rowText(0)).contains("E2E-PAGE-054");
    }
}
