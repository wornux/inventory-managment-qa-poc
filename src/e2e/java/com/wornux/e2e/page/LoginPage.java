package com.wornux.e2e.page;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.wornux.e2e.support.AbstractInventoryIT;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

public final class LoginPage {

    private final AbstractInventoryIT browser;

    public LoginPage(AbstractInventoryIT browser) {
        this.browser = browser;
    }

    public void signIn(String username, String password) {
        browser.$(ButtonElement.class).id("keycloak-login").click();
        browser.waitUntil(ExpectedConditions.urlContains("/realms/wornux/protocol/openid-connect/auth"));
        browser.findElement(By.id("username")).sendKeys(username);
        browser.findElement(By.id("password")).sendKeys(password);
        browser.findElement(By.id("kc-login")).click();
        browser.waitUntil(ExpectedConditions.presenceOfElementLocated(By.id("main-layout")));
    }
}
