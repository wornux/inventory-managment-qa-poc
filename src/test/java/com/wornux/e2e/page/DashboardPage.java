package com.wornux.e2e.page;

import com.wornux.e2e.support.AbstractInventoryIT;
import java.util.NoSuchElementException;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

public final class DashboardPage {

    private final AbstractInventoryIT browser;

    public DashboardPage(AbstractInventoryIT browser) {
        this.browser = browser;
        browser.waitUntil(ExpectedConditions.presenceOfElementLocated(By.id("dashboard-view")));
    }

    public String kpiValue(String label) {
        return browser.getDriver().findElements(By.cssSelector("#dashboard-view .dashboard-kpi")).stream()
                .filter(card -> card.getText().contains(label))
                .findFirst()
                .map(card -> card.findElement(By.cssSelector(".dashboard-kpi-value")))
                .map(WebElement::getText)
                .orElseThrow(() -> new NoSuchElementException("Dashboard KPI not found: " + label));
    }

    public boolean shows(String text) {
        return browser.getDriver()
                .findElement(By.id("dashboard-view"))
                .getText()
                .contains(text);
    }

    public long panelLinkCount() {
        return browser.getDriver()
                .findElements(By.cssSelector("#dashboard-view a.dashboard-panel-link"))
                .size();
    }

    public String chartAccessibleName() {
        browser.waitUntil(driver -> {
            String accessibleName = rawChartAccessibleName();

            return accessibleName != null && !accessibleName.isBlank();
        });

        return rawChartAccessibleName();
    }

    public boolean hasHorizontalOverflow() {
        return (Boolean) browser.executeScript(
                "return document.documentElement.scrollWidth > document.documentElement.clientWidth + 1");
    }

    private String rawChartAccessibleName() {
        return (String) browser.executeScript("""
                const host = document.querySelector('inventory-movement-chart');
                const root = host?.shadowRoot ?? host;
                const chart = root?.querySelector('svg[role="application"]');
                return chart?.getAttribute('aria-label') ?? null;
                """);
    }
}
