package com.wornux.e2e.page;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.sidenav.testbench.SideNavItemElement;
import com.vaadin.testbench.TestBenchElement;
import com.wornux.e2e.support.AbstractInventoryIT;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

public final class ApplicationShell {

    private final AbstractInventoryIT browser;

    public ApplicationShell(AbstractInventoryIT browser) {
        this.browser = browser;
    }

    public ApplicationShell waitUntilLoaded() {
        browser.waitUntil(ExpectedConditions.presenceOfElementLocated(By.id("main-layout")));

        return this;
    }

    public boolean offersNavigationTo(String route) {
        return browser.$(SideNavItemElement.class).withId("nav-" + route).exists();
    }

    public boolean offersOverview() {
        return browser.$(SideNavItemElement.class).withId("nav-overview").exists();
    }

    public void navigateTo(String route) {
        browser.$(SideNavItemElement.class).id("nav-" + route).navigate();
        browser.waitUntil(ExpectedConditions.urlContains("/" + route));
    }

    public void openMobileNavigation() {
        browser.$("vaadin-drawer-toggle").withId("main-layout-toggle").single().click();
    }

    public boolean mobileNavigationButtonIsVisible() {
        return browser.$("vaadin-drawer-toggle")
                .withId("main-layout-toggle")
                .single()
                .isDisplayed();
    }

    public void signOut() {
        TestBenchElement profile =
                browser.$("vaadin-details").withId("profile-drawer").single();
        profile.setProperty("opened", true);
        browser.$(ButtonElement.class).id("sign-out").click();
        browser.waitUntil(ExpectedConditions.urlMatches(".*/login$"));
    }
}
