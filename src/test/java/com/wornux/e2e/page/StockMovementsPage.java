package com.wornux.e2e.page;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.component.grid.testbench.GridElement;
import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.flow.component.textfield.testbench.IntegerFieldElement;
import com.vaadin.flow.component.textfield.testbench.TextAreaElement;
import com.wornux.e2e.support.AbstractInventoryIT;
import java.util.List;

public final class StockMovementsPage {

    private final AbstractInventoryIT browser;

    public StockMovementsPage(AbstractInventoryIT browser) {
        this.browser = browser;
        browser.$("vaadin-master-detail-layout").withId("stock-movements-view").waitForFirst();
    }

    public boolean canRecordMovements() {
        return browser.$(ButtonElement.class).withId("record-movement").exists();
    }

    public StockMovementsPage record(StockMovementDraft movement) {
        attemptToRecord(movement);
        browser.waitUntil(driver -> shows(movement.displayedQuantity()));

        return this;
    }

    public StockMovementsPage attemptToRecord(StockMovementDraft movement) {
        openRecordForm();
        fill(movement);
        save();

        return this;
    }

    public StockMovementsPage submitBlankMovement() {
        openRecordForm();
        save();

        return this;
    }

    public boolean requiredFieldsAreInvalid() {
        return currentProductField().hasAttribute("invalid")
                && currentMovementTypeField().hasAttribute("invalid")
                && currentQuantityField().hasAttribute("invalid");
    }

    public boolean formIsOpen() {
        return CurrentElement.find(
                        browser.$(ButtonElement.class).withId("save-movement").all())
                .isPresent();
    }

    public String errorMessage() {
        browser.waitUntil(driver -> !notifications().isEmpty());

        return notifications().getLast().getText();
    }

    public boolean shows(String text) {
        GridElement movementsGrid = browser.$(GridElement.class).id("stock-movements-grid");
        if (movementsGrid.getRowCount() == 0) {
            return false;
        }

        return movementsGrid.getVisibleRows().stream()
                .anyMatch(row -> row.getText().contains(text));
    }

    private void openRecordForm() {
        browser.$(ButtonElement.class).id("record-movement").click();
        browser.waitUntil(driver -> formIsOpen());
    }

    private void fill(StockMovementDraft movement) {
        currentProductField().selectByText(movement.product());
        currentMovementTypeField().selectByText(movement.type());
        currentQuantityField().setValue(movement.quantity());

        if (!movement.reason().isBlank()) {
            CurrentElement.required(browser.$(TextAreaElement.class)
                            .withId("movement-reason")
                            .all())
                    .setValue(movement.reason());
        }
    }

    private void save() {
        CurrentElement.required(
                        browser.$(ButtonElement.class).withId("save-movement").all())
                .click();
    }

    private ComboBoxElement currentProductField() {
        return CurrentElement.required(
                browser.$(ComboBoxElement.class).withId("movement-product").all());
    }

    private ComboBoxElement currentMovementTypeField() {
        return CurrentElement.required(
                browser.$(ComboBoxElement.class).withId("movement-type").all());
    }

    private IntegerFieldElement currentQuantityField() {
        return CurrentElement.required(
                browser.$(IntegerFieldElement.class).withId("movement-quantity").all());
    }

    private List<NotificationElement> notifications() {
        return browser.$(NotificationElement.class).onPage().all();
    }
}
