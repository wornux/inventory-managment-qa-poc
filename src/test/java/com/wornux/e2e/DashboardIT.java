package com.wornux.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.testbench.BrowserTest;
import com.wornux.catalog.MovementType;
import com.wornux.e2e.page.DashboardPage;
import com.wornux.e2e.support.AbstractInventoryIT;

class DashboardIT extends AbstractInventoryIT {

    @BrowserTest
    void dashboard_renders_inventory_data_and_the_accessible_react_chart() {
        givenProduct("E2E-CRITICAL", "E2E Critical Product", 2, 5);
        givenProduct("E2E-HEALTHY", "E2E Healthy Product", 20, 5);
        givenMovement("E2E-HEALTHY", MovementType.PURCHASE.name(), 8);
        givenMovement("E2E-HEALTHY", MovementType.SALE.name(), -4);
        signInAs(INVENTORY_VIEWER);
        var dashboard = new DashboardPage(this);

        assertThat(dashboard.kpiValue("Active products")).isEqualTo("2");
        assertThat(dashboard.kpiValue("Units on hand")).isEqualTo("22");
        assertThat(dashboard.kpiValue("Inventory value")).isEqualTo("220.00");
        assertThat(dashboard.kpiValue("Critical products")).isEqualTo("1");
        assertThat(dashboard.shows("E2E Critical Product")).isTrue();
        assertThat(dashboard.shows("E2E Healthy Product")).isTrue();
        assertThat(dashboard.shows("System")).isTrue();
        assertThat(dashboard.chartAccessibleName())
                .contains(
                        "Inbound and outbound inventory units for the latest seven activity days.",
                        "8 inbound, 4 outbound");
    }

    @BrowserTest
    void dashboard_remains_usable_on_a_mobile_viewport() {
        givenProduct("E2E-MOBILE", "E2E Mobile Product", 2, 5);
        givenMovement("E2E-MOBILE", MovementType.PURCHASE.name(), 2);
        setViewport(390, 844);
        signInAs(INVENTORY_VIEWER);
        var dashboard = new DashboardPage(this);

        assertThat(dashboard.chartAccessibleName()).isNotBlank();
        assertThat(dashboard.hasHorizontalOverflow()).isFalse();
    }
}
