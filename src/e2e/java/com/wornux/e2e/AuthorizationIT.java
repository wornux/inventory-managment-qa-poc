package com.wornux.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.testbench.BrowserTest;
import com.wornux.e2e.page.ProductsPage;
import com.wornux.e2e.page.StockMovementsPage;
import com.wornux.e2e.support.AbstractInventoryIT;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

class AuthorizationIT extends AbstractInventoryIT {

    @BrowserTest
    void viewer_can_browse_inventory_but_cannot_change_it() {
        var application = signInAs(INVENTORY_VIEWER);

        assertThat(application.offersNavigationTo("products")).isTrue();
        assertThat(application.offersNavigationTo("stock-movements")).isTrue();
        assertThat(application.offersNavigationTo("users")).isFalse();
        assertThat(application.offersNavigationTo("roles")).isFalse();

        application.navigateTo("products");
        assertThat(new ProductsPage(this).canCreateProducts()).isFalse();
    }

    @BrowserTest
    void warehouse_operator_can_record_stock_but_cannot_manage_products() {
        var application = signInAs(WAREHOUSE_OPERATOR);

        application.navigateTo("products");
        assertThat(new ProductsPage(this).canCreateProducts()).isFalse();

        application.navigateTo("stock-movements");
        assertThat(new StockMovementsPage(this).canRecordMovements()).isTrue();
    }

    @BrowserTest
    void viewer_cannot_record_stock_movements_from_the_direct_route() {
        signInAs(INVENTORY_VIEWER);

        open("/stock-movements");

        assertThat(new StockMovementsPage(this).canRecordMovements()).isFalse();
    }

    @BrowserTest
    void inventory_manager_cannot_bypass_administration_navigation_with_a_direct_url() {
        var application = signInAs(INVENTORY_MANAGER);
        assertThat(application.offersNavigationTo("users")).isFalse();

        open("/users");
        waitUntil(ExpectedConditions.textToBePresentInElementLocated(By.tagName("h1"), "Access forbidden"));

        assertThat(getDriver().getPageSource()).contains("Access forbidden");
    }

    @BrowserTest
    void system_administrator_receives_every_navigation_area() {
        var application = signInAs(SYSTEM_ADMINISTRATOR);

        assertThat(application.offersNavigationTo("products")).isTrue();
        assertThat(application.offersNavigationTo("stock-movements")).isTrue();
        assertThat(application.offersNavigationTo("users")).isTrue();
        assertThat(application.offersNavigationTo("roles")).isTrue();
    }
}
