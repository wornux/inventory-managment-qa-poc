package com.wornux.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.testbench.BrowserTest;
import com.wornux.e2e.page.ProductDraft;
import com.wornux.e2e.page.ProductsPage;
import com.wornux.e2e.page.StockMovementDraft;
import com.wornux.e2e.page.StockMovementsPage;
import com.wornux.e2e.support.AbstractInventoryIT;
import com.wornux.e2e.support.E2eFixtures;

class StockMovementIT extends AbstractInventoryIT {

    @BrowserTest
    void stock_entries_and_exits_update_inventory_and_remain_in_the_ledger() {
        var application = signInAs(SYSTEM_ADMINISTRATOR);
        application.navigateTo("products");
        var product = ProductDraft.lowStock("E2E-STOCK", "Stock Widget", E2eFixtures.CATEGORY, E2eFixtures.SUPPLIER);
        new ProductsPage(this).create(product);

        application.navigateTo("stock-movements");
        var movements = new StockMovementsPage(this);

        movements.record(StockMovementDraft.purchase(product.label(), 10));
        assertThat(movements.shows("+10")).isTrue();

        movements.record(StockMovementDraft.sale(product.label(), 4));
        assertThat(movements.shows("-4")).isTrue();

        application.navigateTo("products");
        var products = new ProductsPage(this).searchFor(product.sku());
        assertThat(products.rowTextFor(product.sku())).contains("9");
    }

    @BrowserTest
    void stock_exit_cannot_reduce_inventory_below_zero() {
        String sku = "E2E-LIMIT";
        String name = "Limited Stock";
        givenProduct(sku, name, 2, 1);
        var application = signInAs(SYSTEM_ADMINISTRATOR);
        application.navigateTo("stock-movements");
        var movements = new StockMovementsPage(this);

        movements.attemptToRecord(StockMovementDraft.sale(sku + " - " + name, 3));

        assertThat(movements.errorMessage()).contains("Insufficient stock");
        assertThat(movements.formIsOpen()).isTrue();
        assertThat(movements.shows("-3")).isFalse();

        open("/products");
        var products = new ProductsPage(this).searchFor(sku);
        assertThat(products.rowTextFor(sku)).contains("2");
    }

    @BrowserTest
    void deleting_a_product_preserves_its_movement_history() {
        var application = signInAs(SYSTEM_ADMINISTRATOR);
        application.navigateTo("products");
        var product =
                ProductDraft.lowStock("E2E-HISTORY", "History Widget", E2eFixtures.CATEGORY, E2eFixtures.SUPPLIER);
        var products = new ProductsPage(this);
        products.create(product);

        application.navigateTo("stock-movements");
        new StockMovementsPage(this).record(StockMovementDraft.purchase(product.label(), 1));

        application.navigateTo("products");
        products = new ProductsPage(this).delete(product.sku());
        assertThat(products.shows(product.sku())).isFalse();

        application.navigateTo("stock-movements");
        var movements = new StockMovementsPage(this);
        assertThat(movements.shows(product.sku())).isTrue();
        assertThat(movements.shows("+1")).isTrue();
    }

    @BrowserTest
    void movement_form_keeps_invalid_submission_open_and_explains_required_fields() {
        givenProduct("E2E-VALIDATION", "Validation Product", 5, 1);
        var application = signInAs(SYSTEM_ADMINISTRATOR);
        application.navigateTo("stock-movements");
        var movements = new StockMovementsPage(this);

        movements.submitBlankMovement();

        assertThat(movements.requiredFieldsAreInvalid()).isTrue();
        assertThat(movements.formIsOpen()).isTrue();
        assertThat(movements.errorMessage()).contains("Please fix the highlighted fields");
    }
}
