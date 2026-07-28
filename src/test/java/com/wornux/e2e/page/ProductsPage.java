package com.wornux.e2e.page;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.checkbox.testbench.CheckboxElement;
import com.vaadin.flow.component.combobox.testbench.ComboBoxElement;
import com.vaadin.flow.component.dialog.testbench.DialogElement;
import com.vaadin.flow.component.grid.testbench.GridElement;
import com.vaadin.flow.component.grid.testbench.GridTRElement;
import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.flow.component.textfield.testbench.BigDecimalFieldElement;
import com.vaadin.flow.component.textfield.testbench.IntegerFieldElement;
import com.vaadin.flow.component.textfield.testbench.TextAreaElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.wornux.e2e.support.AbstractInventoryIT;
import java.util.List;
import java.util.Optional;
import org.openqa.selenium.interactions.Actions;

public final class ProductsPage {

    private final AbstractInventoryIT browser;

    public ProductsPage(AbstractInventoryIT browser) {
        this.browser = browser;
        browser.$("vaadin-master-detail-layout").withId("products-view").waitForFirst();
    }

    public boolean canCreateProducts() {
        return browser.$(ButtonElement.class).withId("new-product").exists();
    }

    public ProductsPage create(ProductDraft product) {
        attemptToCreate(product);
        searchFor(product.sku());
        waitForProduct(product.sku());

        return this;
    }

    public ProductsPage attemptToCreate(ProductDraft product) {
        openCreateForm();
        fill(product);
        save();

        return this;
    }

    public ProductsPage edit(String sku, ProductDraft changedProduct) {
        searchFor(sku);
        clickRowAction("Edit");
        fill(changedProduct);
        save();
        searchFor(changedProduct.name());
        waitForProduct(changedProduct.name());

        return this;
    }

    public ProductsPage delete(String sku) {
        searchFor(sku);
        openDeleteConfirmation();
        CurrentElement.required(browser.$(DialogElement.class).onPage().all())
                .$(ButtonElement.class)
                .id("confirm-product-delete")
                .click();
        waitForProductToDisappear(sku);

        return this;
    }

    public boolean formIsOpen() {
        return CurrentElement.find(
                        browser.$(ButtonElement.class).withId("save-product").all())
                .isPresent();
    }

    public String errorMessage() {
        browser.waitUntil(driver -> !notifications().isEmpty());

        return notifications().getLast().getText();
    }

    public long matchingProductCount(String text) {
        return rows().stream().filter(row -> row.getText().contains(text)).count();
    }

    private void openDeleteConfirmation() {
        browser.waitUntil(driver -> {
            clickRowAction("Delete");
            return browser.$(DialogElement.class).onPage().exists();
        });
    }

    public ProductsPage searchFor(String text) {
        browser.$(TextFieldElement.class).id("product-search").setValue(text);
        if (!text.isBlank()) {
            browser.waitUntil(driver -> !rows().isEmpty()
                    && rows().stream()
                            .allMatch(row -> row.getText().toLowerCase().contains(text.toLowerCase())));
        }

        return this;
    }

    public ProductsPage showOnlyLowStock() {
        browser.$(CheckboxElement.class).id("product-low-stock-filter").setChecked(true);
        browser.waitUntil(
                driver -> rows().stream().allMatch(row -> row.getText().contains("LOW STOCK")));

        return this;
    }

    public ProductsPage filterByCategory(String category) {
        browser.$(ComboBoxElement.class).id("product-category-filter").selectByText(category);

        return this;
    }

    public ProductsPage sortProductsDescending() {
        grid().getHeaderCell(0).$("vaadin-grid-sorter").first().click();
        browser.waitUntil(driver -> "desc"
                .equals(grid().getHeaderCell(0).$("vaadin-grid-sorter").first().getAttribute("direction")));
        grid().scrollToRow(0);
        browser.waitUntil(driver -> grid().getRow(0) != null);

        return this;
    }

    public ProductsPage scrollToProductRow(int row) {
        grid().scrollToRow(row);

        return this;
    }

    public ProductsPage loadCompleteResultSet() {
        int estimatedRowCount = productCount();
        grid().scrollToRow(estimatedRowCount - 1);
        browser.waitUntil(driver -> productCount() < estimatedRowCount);

        return this;
    }

    public int productCount() {
        return grid().getRowCount();
    }

    public String rowText(int row) {
        return grid().getRow(row).getText();
    }

    public boolean shows(String text) {
        return rows().stream().anyMatch(row -> row.getText().contains(text));
    }

    public boolean offersRowAction(String action) {
        return rows().stream()
                .flatMap(row -> row.getCell(grid().getVisibleColumns().getLast()).$(ButtonElement.class).all().stream())
                .anyMatch(button -> action.equals(button.getText()));
    }

    public String rowTextFor(String text) {
        return rows().stream()
                .filter(row -> row.getText().contains(text))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No visible product row contained " + text))
                .getText();
    }

    public ProductsPage submitBlankProduct() {
        openCreateForm();
        save();

        return this;
    }

    public boolean requiredFieldsAreInvalid() {
        return CurrentElement.required(browser.$(TextFieldElement.class)
                                .withId("product-sku")
                                .all())
                        .hasAttribute("invalid")
                && CurrentElement.required(browser.$(TextFieldElement.class)
                                .withId("product-name")
                                .all())
                        .hasAttribute("invalid");
    }

    private void openCreateForm() {
        browser.$(ButtonElement.class).id("new-product").click();
        browser.waitUntil(driver -> formIsOpen());
    }

    private void fill(ProductDraft product) {
        CurrentElement.required(
                        browser.$(TextFieldElement.class).withId("product-sku").all())
                .setValue(product.sku());
        CurrentElement.required(
                        browser.$(TextFieldElement.class).withId("product-name").all())
                .setValue(product.name());
        CurrentElement.required(browser.$(TextAreaElement.class)
                        .withId("product-description")
                        .all())
                .setValue(product.description());
        CurrentElement.required(browser.$(BigDecimalFieldElement.class)
                        .withId("product-unit-price")
                        .all())
                .setValue(product.unitPrice());
        CurrentElement.required(browser.$(IntegerFieldElement.class)
                        .withId("product-quantity")
                        .all())
                .setValue(product.quantity());
        CurrentElement.required(browser.$(IntegerFieldElement.class)
                        .withId("product-minimum-stock")
                        .all())
                .setValue(product.minimumStock());
        CurrentElement.required(browser.$(ComboBoxElement.class)
                        .withId("product-category")
                        .all())
                .selectByText(product.category());
        CurrentElement.required(browser.$(ComboBoxElement.class)
                        .withId("product-supplier")
                        .all())
                .selectByText(product.supplier());
    }

    private void save() {
        CurrentElement.required(
                        browser.$(ButtonElement.class).withId("save-product").all())
                .click();
    }

    private void clickRowAction(String action) {
        if ("Delete".equals(action)) {
            grid().scrollToColumn(grid().getVisibleColumns().getLast());
        }

        browser.waitUntil(driver -> findRowAction(action).isPresent());
        ButtonElement button = findRowAction(action).orElseThrow();
        if (!"Delete".equals(action)) {
            button.click();
            return;
        }

        new Actions(browser.getDriver()).moveToElement(button).click().perform();
    }

    private Optional<ButtonElement> findRowAction(String action) {
        return rows().stream()
                .flatMap(row -> row.getCell(grid().getVisibleColumns().getLast()).$(ButtonElement.class).all().stream())
                .filter(ButtonElement::isDisplayed)
                .filter(this::isTopmost)
                .filter(candidate -> action.equals(candidate.getText()))
                .findFirst();
    }

    private boolean isTopmost(ButtonElement button) {
        return Boolean.TRUE.equals(browser.executeScript("""
                const element = arguments[0];
                const bounds = element.getBoundingClientRect();
                const topmost = document.elementFromPoint(
                    bounds.left + bounds.width / 2,
                    bounds.top + bounds.height / 2
                );
                return topmost === element
                    || element.contains(topmost)
                    || topmost?.getRootNode()?.host === element;
                """, button));
    }

    private void waitForProduct(String text) {
        browser.waitUntil(driver -> shows(text));
    }

    private void waitForProductToDisappear(String text) {
        browser.waitUntil(driver -> !shows(text));
    }

    private GridElement grid() {
        return browser.$(GridElement.class).id("products-grid");
    }

    private List<GridTRElement> rows() {
        GridElement productGrid = grid();
        if (productGrid.getRowCount() == 0) {
            return List.of();
        }

        return productGrid.getVisibleRows();
    }

    private List<NotificationElement> notifications() {
        return browser.$(NotificationElement.class).onPage().all();
    }
}
