package com.wornux.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.testbench.BrowserTest;
import com.wornux.e2e.support.AbstractInventoryIT;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

class AuthenticationIT extends AbstractInventoryIT {

    @BrowserTest
    void anonymous_visitor_is_sent_to_the_real_login_flow() {
        open("/products");

        waitUntil(ExpectedConditions.urlContains("/realms/wornux/protocol/openid-connect/auth"));

        assertThat(getDriver().getCurrentUrl()).contains("/realms/wornux/protocol/openid-connect/auth");
        assertThat(findElement(By.id("username")).isDisplayed()).isTrue();
        assertThat(findElement(By.id("password")).isDisplayed()).isTrue();
    }

    @BrowserTest
    void signed_in_user_can_end_the_session() {
        var application = signInAs(SYSTEM_ADMINISTRATOR);

        application.signOut();
        open("/products");
        waitUntil(ExpectedConditions.urlContains("/realms/wornux/protocol/openid-connect/auth"));

        assertThat(getDriver().getCurrentUrl()).contains("/realms/wornux/protocol/openid-connect/auth");
    }
}
