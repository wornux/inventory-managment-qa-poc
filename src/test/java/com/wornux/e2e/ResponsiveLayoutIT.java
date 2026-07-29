package com.wornux.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.testbench.BrowserTest;
import com.wornux.e2e.support.AbstractInventoryIT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

class ResponsiveLayoutIT extends AbstractInventoryIT {

    @BrowserTest
    void inventory_navigation_remains_usable_on_a_mobile_viewport() throws IOException {
        setViewport(500, 780);
        var application = signInAs(INVENTORY_VIEWER);

        assertThat(application.mobileNavigationButtonIsVisible()).isTrue();
        application.openMobileNavigation();
        application.navigateTo("products");

        long overflow = ((Number) executeScript(
                        "return document.documentElement.scrollWidth - document.documentElement.clientWidth"))
                .longValue();
        assertThat(overflow).isLessThanOrEqualTo(1);

        Path evidence = Path.of("target/e2e/screenshots/mobile-products.png");
        Files.createDirectories(evidence.getParent());
        Files.write(evidence, ((TakesScreenshot) getDriver()).getScreenshotAs(OutputType.BYTES));
    }
}
