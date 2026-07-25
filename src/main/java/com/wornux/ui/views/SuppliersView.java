package com.wornux.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierException;
import com.wornux.catalog.SupplierFilter;
import com.wornux.catalog.SupplierRequest;
import com.wornux.catalog.SupplierService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.AccessDeniedException;

@Route("suppliers")
@PageTitle("Suppliers")
@PermitAll
public class SuppliersView extends Main {

    private enum FormMode {
        CREATE,
        EDIT,
        VIEW
    }

    private final SupplierService supplierService;
    private final Grid<Supplier> grid = new Grid<>(Supplier.class, false);
    private final TextField search = new TextField();
    private final ComboBox<String> activeFilter = new ComboBox<>("Status");
    private final Dialog sidebar = new Dialog();
    private final Dialog deactivateDialog = new Dialog();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<SupplierRequest> binder = new BeanValidationBinder<>(SupplierRequest.class);
    private final SupplierRequest formData = new SupplierRequest();
    private final TextField name = new TextField("Name");
    private final TextField contactName = new TextField("Contact Name");
    private final EmailField email = new EmailField("Email");
    private final TextField phone = new TextField("Phone");
    private final Checkbox active = new Checkbox("Active");
    private final H1 sidebarTitle = new H1();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private final Button edit = new Button("Edit");
    private final H1 deactivateTitle = new H1("Deactivate this supplier?");
    private final Span deactivateText = new Span();
    private Supplier selectedSupplier;
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;

    public SuppliersView(SupplierService supplierService) {
        this.supplierService = supplierService;
        addClassName("products-view");
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
        var title = new H1("Suppliers");
        var subtitle = new Span("Sourcing contacts, assignment availability, and supplier status.");
        subtitle.addClassName("products-subtitle");
        header.add(title, subtitle);

        return header;
    }

    private Component buildToolbar() {
        var toolbar = new HorizontalLayout();
        toolbar.addClassName("products-toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.END);

        Button newSupplier = new Button("New Supplier", event -> openCreate());
        newSupplier.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newSupplier.setVisible(supplierService.canCreateSuppliers());

        toolbar.add(search, activeFilter, newSupplier);
        toolbar.setFlexGrow(1, search);

        return toolbar;
    }

    private void configureFilters() {
        search.setPlaceholder("Search supplier or contact");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> refreshGrid());

        activeFilter.setItems("Active", "Inactive", "All");
        activeFilter.setValue("Active");
        activeFilter.setClearButtonVisible(false);
        activeFilter.addValueChangeListener(event -> refreshGrid());
    }

    private void configureGrid() {
        grid.addClassName("products-grid");
        grid.setSizeFull();
        grid.addColumn(supplierRenderer())
                .setHeader("Supplier")
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(2);
        grid.addColumn(contactRenderer())
                .setHeader("Contact")
                .setAutoWidth(true)
                .setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(this::activeBadge))
                .setHeader("Active")
                .setAutoWidth(true);
        grid.addColumn(supplier -> supplierService.productCount(supplier.getId()))
                .setHeader("Product Count")
                .setSortable(true)
                .setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::actions))
                .setHeader("Actions")
                .setAutoWidth(true);
        grid.addItemClickListener(event -> openView(event.getItem()));
    }

    private LitRenderer<Supplier> supplierRenderer() {
        return LitRenderer.<Supplier>of("""
                <div class="product-cell">
                  <strong>${item.name}</strong>
                  <span>${item.contactName}</span>
                </div>
                """)
                .withProperty("name", Supplier::getName)
                .withProperty(
                        "contactName",
                        supplier -> supplier.getContactName() == null ? "No contact name" : supplier.getContactName());
    }

    private LitRenderer<Supplier> contactRenderer() {
        return LitRenderer.<Supplier>of("""
                <div class="product-cell">
                  <strong>${item.email}</strong>
                  <span>${item.phone}</span>
                </div>
                """)
                .withProperty("email", supplier -> supplier.getEmail() == null ? "No email" : supplier.getEmail())
                .withProperty("phone", supplier -> supplier.getPhone() == null ? "No phone" : supplier.getPhone());
    }

    private Component activeBadge(Supplier supplier) {
        Span badge = new Span(supplier.isActive() ? "Active" : "Inactive");
        badge.addClassNames("status-badge", supplier.isActive() ? "active-yes" : "active-no");

        return badge;
    }

    private Component actions(Supplier supplier) {
        var layout = new HorizontalLayout();
        layout.addClassName("row-actions");
        Button view = new Button("View", event -> openView(supplier));
        layout.add(view);

        if (supplierService.canUpdateSuppliers()) {
            layout.add(new Button("Edit", event -> openEdit(supplier)));
        }

        if (supplierService.canDeleteSuppliers() && supplier.isActive()) {
            Button deactivate = new Button("Deactivate", event -> confirmDeactivate(supplier));
            deactivate.addThemeVariants(ButtonVariant.LUMO_ERROR);
            layout.add(deactivate);
        }

        return layout;
    }

    private void configureSidebar() {
        sidebar.addClassName("product-sidebar");
        sidebar.setModal(false);
        sidebar.setDraggable(false);
        sidebar.setResizable(false);
        sidebar.setCloseOnEsc(true);
        sidebar.setCloseOnOutsideClick(true);
        sidebar.addOpenedChangeListener(event -> {
            if (!event.isOpened() && dirty && mode != FormMode.VIEW) {
                sidebar.open();
                dirtyDialog.open();
            }
        });

        configureFields();
        bindForm();

        var form = new FormLayout();
        form.add(name, contactName, email, phone, active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        save.addClickListener(event -> saveSupplier());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());
        edit.addClickListener(event -> {
            if (selectedSupplier != null) {
                openEdit(selectedSupplier);
            }
        });

        var footer = new HorizontalLayout(save, cancel, close, edit);
        footer.addClassName("sidebar-footer");

        var content = new VerticalLayout(sidebarTitle, form, footer);
        content.addClassName("sidebar-content");
        sidebar.add(content);
    }

    private void configureFields() {
        name.setRequiredIndicatorVisible(true);
        name.setValueChangeMode(ValueChangeMode.EAGER);
        contactName.setValueChangeMode(ValueChangeMode.EAGER);
        email.setValueChangeMode(ValueChangeMode.EAGER);
        email.setErrorMessage("Invalid email address.");
        phone.setValueChangeMode(ValueChangeMode.EAGER);
        active.setValue(true);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(name)
                .asRequired("Supplier name is required.")
                .bind(SupplierRequest::getName, SupplierRequest::setName);
        binder.bind(contactName, SupplierRequest::getContactName, SupplierRequest::setContactName);
        binder.bind(email, SupplierRequest::getEmail, SupplierRequest::setEmail);
        binder.bind(phone, SupplierRequest::getPhone, SupplierRequest::setPhone);
        binder.bind(active, SupplierRequest::isActive, SupplierRequest::setActive);
    }

    private void configureDialogs() {
        Button confirm = new Button("Deactivate", event -> deactivateSelected());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancelDeactivate = new Button("Cancel", event -> deactivateDialog.close());
        deactivateDialog.add(
                new VerticalLayout(deactivateTitle, deactivateText, new HorizontalLayout(confirm, cancelDeactivate)));

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
        selectedSupplier = null;
        sidebarTitle.setText("New Supplier");
        resetForm(new SupplierRequest());
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openEdit(Supplier supplier) {
        selectedSupplier = supplierService.get(supplier.getId());
        mode = FormMode.EDIT;
        sidebarTitle.setText("Edit Supplier");
        resetForm(fromSupplier(selectedSupplier));
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openView(Supplier supplier) {
        selectedSupplier = supplierService.get(supplier.getId());
        mode = FormMode.VIEW;
        sidebarTitle.setText("Supplier Details");
        resetForm(fromSupplier(selectedSupplier));
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        edit.setVisible(supplierService.canUpdateSuppliers());
        dirty = false;
        sidebar.open();
    }

    private void resetForm(SupplierRequest request) {
        formData.setName(request.getName());
        formData.setContactName(request.getContactName());
        formData.setEmail(request.getEmail());
        formData.setPhone(request.getPhone());
        formData.setActive(request.isActive());
        formData.setVersion(request.getVersion());
        binder.readBean(formData);
        name.setInvalid(false);
        contactName.setInvalid(false);
        email.setInvalid(false);
        phone.setInvalid(false);
    }

    private SupplierRequest fromSupplier(Supplier supplier) {
        var request = new SupplierRequest();
        request.setName(supplier.getName());
        request.setContactName(supplier.getContactName());
        request.setEmail(supplier.getEmail());
        request.setPhone(supplier.getPhone());
        request.setActive(supplier.isActive());
        request.setVersion(supplier.getVersion());

        return request;
    }

    private void saveSupplier() {
        if (!binder.writeBeanIfValid(formData)) {
            showError("Please fix the highlighted fields.");
            return;
        }

        try {
            if (mode == FormMode.CREATE) {
                supplierService.create(formData);
                showSuccess("Supplier created.");
            } else if (mode == FormMode.EDIT && selectedSupplier != null) {
                supplierService.update(selectedSupplier.getId(), formData);
                showSuccess("Supplier updated.");
            }

            dirty = false;
            sidebar.close();
            refreshGrid();
        } catch (SupplierException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDeactivate(Supplier supplier) {
        selectedSupplier = supplier;
        long activeProducts = supplierService.activeProductCount(supplier.getId());

        if (activeProducts > 0) {
            deactivateText.setText(
                    "This supplier has " + activeProducts
                            + " products. Deactivating the supplier will not affect existing products, but new products cannot be assigned to this supplier.");
        } else {
            deactivateText.setText(
                    "Products sourced from this supplier will still exist but this supplier will not be available for new product assignments.");
        }

        deactivateDialog.open();
    }

    private void deactivateSelected() {
        try {
            supplierService.deactivate(selectedSupplier.getId());
            deactivateDialog.close();
            dirty = false;
            sidebar.close();
            showSuccess("Supplier deactivated.");
            refreshGrid();
        } catch (SupplierException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void requestClose() {
        if (dirty && mode != FormMode.VIEW) {
            dirtyDialog.open();
            return;
        }

        dirty = false;
        sidebar.close();
    }

    private void setReadOnly(boolean readOnly) {
        name.setReadOnly(readOnly);
        contactName.setReadOnly(readOnly);
        email.setReadOnly(readOnly);
        phone.setReadOnly(readOnly);
        active.setReadOnly(readOnly);
    }

    private void refreshGrid() {
        grid.setItems(supplierService.search(new SupplierFilter(search.getValue(), activeFilterValue())));
    }

    private Boolean activeFilterValue() {
        if ("Active".equals(activeFilter.getValue())) {
            return true;
        }

        if ("Inactive".equals(activeFilter.getValue())) {
            return false;
        }

        return null;
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
