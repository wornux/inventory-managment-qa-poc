package com.wornux.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.catalog.MovementType;
import com.wornux.catalog.Product;
import com.wornux.catalog.StockMovement;
import com.wornux.catalog.StockMovementException;
import com.wornux.catalog.StockMovementFilter;
import com.wornux.catalog.StockMovementRequest;
import com.wornux.catalog.StockMovementService;
import jakarta.annotation.security.PermitAll;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

@Route("stock-movements")
@PageTitle("Stock Movements")
@PermitAll
public class StockMovementsView extends Main {

    private enum FormMode {
        CREATE,
        VIEW
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final StockMovementService stockMovementService;
    private final Grid<StockMovement> grid = new Grid<>(StockMovement.class, false);
    private final DatePicker fromDate = new DatePicker("From");
    private final DatePicker toDate = new DatePicker("To");
    private final ComboBox<Product> productFilter = new ComboBox<>("Product");
    private final ComboBox<MovementType> typeFilter = new ComboBox<>("Type");
    private final ComboBox<String> userFilter = new ComboBox<>("User");
    private final Dialog sidebar = new Dialog();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<StockMovementRequest> binder =
            new BeanValidationBinder<>(StockMovementRequest.class);
    private final StockMovementRequest formData = new StockMovementRequest();
    private final TextField createdAt = new TextField("Created at");
    private final TextField user = new TextField("User");
    private final ComboBox<Product> product = new ComboBox<>("Product");
    private final ComboBox<MovementType> movementType = new ComboBox<>("Movement type");
    private final IntegerField quantityDelta = new IntegerField("Quantity delta");
    private final TextArea reason = new TextArea("Reason");
    private final Span noProductsMessage = new Span("No products available.");
    private final H1 sidebarTitle = new H1();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private List<Product> products = new ArrayList<>();
    private List<String> usernames = new ArrayList<>();
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;

    public StockMovementsView(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
        addClassNames("products-view", "movements-view");
        products = stockMovementService.activeProducts();
        usernames = stockMovementService.movementUsernames();
        configureFilters();
        configureGrid();
        configureSidebar();
        configureDialogs();

        add(buildHeader(), buildToolbar(), grid);
        refreshGrid();
    }

    private Component buildHeader() {
        var header = new Header();
        header.addClassName("products-header");
        var title = new H1("Stock Movements");
        var subtitle = new Span("Append-only ledger for product stock changes and attribution.");
        subtitle.addClassName("products-subtitle");
        header.add(title, subtitle);

        return header;
    }

    private Component buildToolbar() {
        var toolbar = new HorizontalLayout();
        toolbar.addClassName("products-toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.END);

        Button recordMovement = new Button("Record Movement", event -> openCreate());
        recordMovement.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        recordMovement.setVisible(stockMovementService.canCreateMovements());

        toolbar.add(fromDate, toDate, productFilter, typeFilter, userFilter, recordMovement);

        return toolbar;
    }

    private void configureFilters() {
        fromDate.setClearButtonVisible(true);
        fromDate.addValueChangeListener(event -> refreshGrid());
        toDate.setClearButtonVisible(true);
        toDate.addValueChangeListener(event -> refreshGrid());

        productFilter.setItems(products);
        productFilter.setItemLabelGenerator(this::productLabel);
        productFilter.setClearButtonVisible(true);
        productFilter.addValueChangeListener(event -> refreshGrid());

        typeFilter.setItems(MovementType.values());
        typeFilter.setItemLabelGenerator(MovementType::displayName);
        typeFilter.setClearButtonVisible(true);
        typeFilter.addValueChangeListener(event -> refreshGrid());

        userFilter.setItems(usernames);
        userFilter.setClearButtonVisible(true);
        userFilter.addValueChangeListener(event -> refreshGrid());
    }

    private void configureGrid() {
        grid.addClassName("products-grid");
        grid.setSizeFull();
        grid.addColumn(movement -> formatInstant(movement.getCreatedAt()))
                .setHeader("Created At")
                .setAutoWidth(true)
                .setSortable(true);
        grid.addColumn(productRenderer())
                .setHeader("Product")
                .setAutoWidth(true)
                .setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(this::movementTypeBadge))
                .setHeader("Movement Type")
                .setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::quantityDeltaLabel))
                .setHeader("Quantity Delta")
                .setAutoWidth(true);
        grid.addColumn(movement -> movement.getUser() == null
                        ? "System"
                        : movement.getUser().getUsername())
                .setHeader("User")
                .setAutoWidth(true);
        grid.addColumn(movement -> movement.getReason() == null ? "None" : movement.getReason())
                .setHeader("Reason")
                .setFlexGrow(2);
        grid.addItemClickListener(event -> openView(event.getItem()));
    }

    private LitRenderer<StockMovement> productRenderer() {
        return LitRenderer.<StockMovement>of("""
                <div class="product-cell">
                  <strong>${item.name}</strong>
                  <span>${item.sku}</span>
                </div>
                """)
                .withProperty("name", movement -> movement.getProduct().getName())
                .withProperty("sku", movement -> movement.getProduct().getSku());
    }

    private Component movementTypeBadge(StockMovement movement) {
        Span badge = new Span(movement.getMovementType().displayName());
        badge.addClassNames("status-badge", "movement-type", movementCssClass(movement.getMovementType()));

        return badge;
    }

    private Component quantityDeltaLabel(StockMovement movement) {
        Integer value = movement.getQuantityDelta();
        Span label = new Span(value > 0 ? "+" + value : String.valueOf(value));
        label.addClassName(value > 0 ? "quantity-positive" : "quantity-negative");

        return label;
    }

    private void configureSidebar() {
        sidebar.addClassName("product-sidebar");
        sidebar.setModal(false);
        sidebar.setDraggable(false);
        sidebar.setResizable(false);
        sidebar.setCloseOnEsc(true);
        sidebar.setCloseOnOutsideClick(true);
        sidebar.addOpenedChangeListener(event -> {
            if (!event.isOpened() && dirty && mode == FormMode.CREATE) {
                sidebar.open();
                dirtyDialog.open();
            }
        });

        configureFields();
        bindForm();

        var metadata = new FormLayout(createdAt, user);
        metadata.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        var form = new FormLayout(product, movementType, quantityDelta, reason);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        noProductsMessage.addClassName("products-subtitle");

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        save.addClickListener(event -> saveMovement());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());

        var footer = new HorizontalLayout(save, cancel, close);
        footer.addClassName("sidebar-footer");

        var content = new VerticalLayout(sidebarTitle, metadata, noProductsMessage, form, footer);
        content.addClassName("sidebar-content");
        sidebar.add(content);
    }

    private void configureFields() {
        createdAt.setReadOnly(true);
        user.setReadOnly(true);
        product.setItems(products);
        product.setItemLabelGenerator(this::productLabel);
        product.setRequiredIndicatorVisible(true);
        movementType.setItems(MovementType.values());
        movementType.setItemLabelGenerator(MovementType::displayName);
        movementType.setRequiredIndicatorVisible(true);
        movementType.addValueChangeListener(event -> updateQuantityForType(event.getValue()));
        quantityDelta.setRequiredIndicatorVisible(true);
        quantityDelta.setValueChangeMode(ValueChangeMode.EAGER);
        reason.setMaxLength(500);
        reason.setValueChangeMode(ValueChangeMode.EAGER);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(product)
                .asRequired("Product is required.")
                .bind(
                        this::productFromRequest,
                        (request, value) -> request.setProductId(value == null ? null : value.getId()));
        binder.forField(movementType)
                .asRequired("Movement type is required.")
                .bind(StockMovementRequest::getMovementType, StockMovementRequest::setMovementType);
        binder.forField(quantityDelta)
                .asRequired("Quantity delta is required.")
                .withValidator(value -> value != 0, "Quantity delta must not be zero.")
                .bind(StockMovementRequest::getQuantityDelta, StockMovementRequest::setQuantityDelta);
        binder.forField(reason)
                .withValidator(
                        value -> !requiresReason() || !trimToNull(value).isEmpty(),
                        "Reason is required for this movement type.")
                .bind(StockMovementRequest::getReason, StockMovementRequest::setReason);
    }

    private void configureDialogs() {
        var dirtyTitle = new H1("Unsaved changes");
        var dirtyText = new Span("You have unsaved changes. Discard them?");
        Button discard = new Button("Discard", event -> {
            dirty = false;
            dirtyDialog.close();
            sidebar.close();
        });
        discard.addThemeVariants(ButtonVariant.LUMO_ERROR);
        Button stay = new Button("Cancel", event -> dirtyDialog.close());
        dirtyDialog.add(new VerticalLayout(dirtyTitle, dirtyText, new HorizontalLayout(discard, stay)));
    }

    private void openCreate() {
        mode = FormMode.CREATE;
        sidebarTitle.setText("Record Movement");
        createdAt.clear();
        user.clear();
        noProductsMessage.setVisible(products.isEmpty());
        resetForm(new StockMovementRequest());
        setReadOnly(false);
        save.setVisible(true);
        save.setEnabled(!products.isEmpty());
        cancel.setVisible(true);
        close.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openView(StockMovement movement) {
        mode = FormMode.VIEW;
        sidebarTitle.setText("Movement Details");
        createdAt.setValue(formatInstant(movement.getCreatedAt()));
        user.setValue(movement.getUser() == null ? "System" : movement.getUser().getUsername());
        noProductsMessage.setVisible(false);
        resetForm(fromMovement(movement));
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        dirty = false;
        sidebar.open();
    }

    private void resetForm(StockMovementRequest request) {
        formData.setProductId(request.getProductId());
        formData.setMovementType(request.getMovementType());
        formData.setQuantityDelta(request.getQuantityDelta());
        formData.setReason(request.getReason());
        binder.readBean(formData);
        updateReasonState();
        clearValidationErrors();
    }

    private StockMovementRequest fromMovement(StockMovement movement) {
        var request = new StockMovementRequest();
        request.setProductId(movement.getProduct().getId());
        request.setMovementType(movement.getMovementType());
        request.setQuantityDelta(movement.getQuantityDelta());
        request.setReason(movement.getReason());

        return request;
    }

    private void saveMovement() {
        if (!binder.writeBeanIfValid(formData)) {
            showError("Please fix the highlighted fields.");
            return;
        }

        try {
            stockMovementService.recordStockMovement(formData);
            showSuccess("Movement recorded.");
            products = stockMovementService.activeProducts();
            product.setItems(products);
            productFilter.setItems(products);
            usernames = stockMovementService.movementUsernames();
            userFilter.setItems(usernames);
            dirty = false;
            sidebar.close();
            refreshGrid();
        } catch (StockMovementException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void requestClose() {
        if (dirty && mode == FormMode.CREATE) {
            dirtyDialog.open();
            return;
        }

        dirty = false;
        sidebar.close();
    }

    private void setReadOnly(boolean readOnly) {
        product.setReadOnly(readOnly);
        movementType.setReadOnly(readOnly);
        quantityDelta.setReadOnly(readOnly);
        reason.setReadOnly(readOnly || !requiresReason());
    }

    private Product productFromRequest(StockMovementRequest request) {
        return products.stream()
                .filter(item -> item.getId().equals(request.getProductId()))
                .findFirst()
                .orElse(null);
    }

    private void updateQuantityForType(MovementType type) {
        updateReasonState();

        if (type == null || quantityDelta.isReadOnly()) {
            return;
        }

        int value = Math.max(1, Math.abs(quantityDelta.getValue() == null ? 1 : quantityDelta.getValue()));
        quantityDelta.setValue(type.isPositive() ? value : -value);
    }

    private void updateReasonState() {
        boolean reasonRequired = requiresReason();
        reason.setRequiredIndicatorVisible(reasonRequired);
        reason.setReadOnly(mode == FormMode.VIEW || !reasonRequired);

        if (!reasonRequired && mode == FormMode.CREATE) {
            reason.clear();
        }
    }

    private boolean requiresReason() {
        MovementType selectedType = movementType.getValue();

        return selectedType != null && selectedType.isReasonRequired();
    }

    private void clearValidationErrors() {
        product.setInvalid(false);
        movementType.setInvalid(false);
        quantityDelta.setInvalid(false);
        reason.setInvalid(false);
    }

    private void refreshGrid() {
        grid.setItems(stockMovementService.search(new StockMovementFilter(
                startOfDay(fromDate.getValue()),
                exclusiveEndOfDay(toDate.getValue()),
                productFilter.getValue() == null
                        ? null
                        : productFilter.getValue().getId(),
                typeFilter.getValue(),
                userFilter.getValue())));
    }

    private Instant startOfDay(LocalDate value) {
        return value == null ? null : value.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant exclusiveEndOfDay(LocalDate value) {
        return value == null
                ? null
                : value.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private String productLabel(Product product) {
        return product == null ? "" : product.getSku() + " - " + product.getName();
    }

    private String movementCssClass(MovementType type) {
        return "movement-" + type.name().toLowerCase();
    }

    private String formatInstant(Instant value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private String trimToNull(String value) {
        return value == null ? "" : value.trim();
    }

    private void showSuccess(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
