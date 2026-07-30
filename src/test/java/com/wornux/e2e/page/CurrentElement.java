package com.wornux.e2e.page;

import java.util.List;
import java.util.Optional;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;

final class CurrentElement {

    private CurrentElement() {}

    static <T extends WebElement> T required(List<T> elements) {
        return find(elements).orElseThrow(() -> new AssertionError("No current Vaadin element was found"));
    }

    static <T extends WebElement> Optional<T> find(List<T> elements) {
        for (int index = elements.size() - 1; index >= 0; index--) {
            T element = elements.get(index);
            try {
                if (element.isDisplayed()) {
                    return Optional.of(element);
                }
            } catch (StaleElementReferenceException ignored) {
                // Vaadin removes the previous detail drawer while the next one opens.
            }
        }

        return Optional.empty();
    }
}
