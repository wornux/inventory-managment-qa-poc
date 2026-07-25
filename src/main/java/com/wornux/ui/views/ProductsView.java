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
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.catalog.Category;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductException;
import com.wornux.catalog.ProductFilter;
import com.wornux.catalog.ProductRequest;
import com.wornux.catalog.ProductService;
import com.wornux.catalog.Supplier;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

@Route("products")
@PageTitle("Products")
@PermitAll
public class ProductsView extends Main {

    private enum FormMode {
        CREATE, EDIT, VIEW
    }

    private final ProductService productService;
    private final Grid<Product> grid = new Grid<>(Product.class, false);
    private final TextField search = new TextField();
    private final ComboBox<Category> categoryFilter = new ComboBox<>("Category");
    private final ComboBox<Supplier> supplierFilter = new ComboBox<>("Supplier");
    private final ComboBox<String> activeFilter = new ComboBox<>("Status");
    private final Checkbox lowStockFilter = new Checkbox("Low stock");
    private final Dialog sidebar = new Dialog();
    private final Dialog deleteDialog = new Dialog();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<ProductRequest> binder = new BeanValidationBinder<>(ProductRequest.class);
    private final ProductRequest formData = new ProductRequest();
    private final TextField sku = new TextField("SKU");
    private final TextField name = new TextField("Name");
    private final TextArea description = new TextArea("Description");
    private final BigDecimalField unitPrice = new BigDecimalField("Unit price");
    private final IntegerField quantityOnHand = new IntegerField("Quantity on hand");
    private final IntegerField minimumStock = new IntegerField("Minimum stock");
    private final ComboBox<Category> category = new ComboBox<>("Category");
    private final ComboBox<Supplier> supplier = new ComboBox<>("Supplier");
    private final Checkbox active = new Checkbox("Active");
    private final H1 sidebarTitle = new H1();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private List<Category> categories = new ArrayList<>();
    private List<Supplier> suppliers = new ArrayList<>();
    private Product selectedProduct;
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;

    public ProductsView(ProductService productService) {
        this.productService = productService;
        addClassName("products-view");
        categories = productService.activeCategories();
        suppliers = productService.activeSuppliers();
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
        var title = new H1("Products");
        var subtitle = new Span("Catalog records, pricing, stock thresholds, and availability.");
        subtitle.addClassName("products-subtitle");
        header.add(title, subtitle);
        return header;
    }

    private Component buildToolbar() {
        var toolbar = new HorizontalLayout();
        toolbar.addClassName("products-toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.END);

        Button newProduct = new Button("New Product", event -> openCreate());
        newProduct.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newProduct.setVisible(productService.canCreateProducts());

        toolbar.add(search, categoryFilter, supplierFilter, activeFilter, lowStockFilter, newProduct);
        toolbar.setFlexGrow(1, search);
        return toolbar;
    }

    private void configureFilters() {
        search.setPlaceholder("Search SKU or name");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> refreshGrid());

        categoryFilter.setItems(categories);
        categoryFilter.setItemLabelGenerator(Category::getName);
        categoryFilter.setClearButtonVisible(true);
        categoryFilter.addValueChangeListener(event -> refreshGrid());

        supplierFilter.setItems(suppliers);
        supplierFilter.setItemLabelGenerator(Supplier::getName);
        supplierFilter.setClearButtonVisible(true);
        supplierFilter.addValueChangeListener(event -> refreshGrid());

        activeFilter.setItems("Active", "Inactive", "All");
        activeFilter.setValue("Active");
        activeFilter.setClearButtonVisible(false);
        activeFilter.addValueChangeListener(event -> refreshGrid());

        lowStockFilter.addValueChangeListener(event -> refreshGrid());
    }

    private void configureGrid() {
        grid.addClassName("products-grid");
        grid.setSizeFull();
        grid.addColumn(productRenderer())
                .setHeader("Product")
                .setSortable(true)
                .setAutoWidth(true)
                .setFlexGrow(2);
        grid.addColumn(product -> product.getCategory().getName()).setHeader("Category").setSortable(true);
        grid.addColumn(product -> product.getSupplier() == null ? "None" : product.getSupplier().getName())
                .setHeader("Supplier")
                .setSortable(true);
        grid.addColumn(Product::getUnitPrice).setHeader("Unit Price").setSortable(true);
        grid.addColumn(Product::getQuantityOnHand).setHeader("Quantity").setSortable(true);
        grid.addColumn(Product::getMinimumStock).setHeader("Minimum").setSortable(true);
        grid.addColumn(new ComponentRenderer<>(this::stockStatusBadge)).setHeader("Stock Status").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::activeBadge)).setHeader("Active").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::actions)).setHeader("Actions").setAutoWidth(true);
        grid.addItemClickListener(event -> openView(event.getItem()));
    }

    private LitRenderer<Product> productRenderer() {
        return LitRenderer.<Product>of("""
                <div class="product-cell">
                  <strong>${item.name}</strong>
                  <span>${item.sku}</span>
                </div>
                """)
                .withProperty("name", Product::getName)
                .withProperty("sku", Product::getSku);
    }

    private Component stockStatusBadge(Product product) {
        Span badge = new Span(product.getStockStatus());
        badge.addClassNames("status-badge", product.isLowStock() ? "stock-low" : "stock-ok");
        return badge;
    }

    private Component activeBadge(Product product) {
        Span badge = new Span(product.isActive() ? "Active" : "Inactive");
        badge.addClassNames("status-badge", product.isActive() ? "active-yes" : "active-no");
        return badge;
    }

    private Component actions(Product product) {
        var layout = new HorizontalLayout();
        layout.addClassName("row-actions");
        Button view = new Button("View", event -> openView(product));
        layout.add(view);
        if (productService.canUpdateProducts()) {
            layout.add(new Button("Edit", event -> openEdit(product)));
        }
        if (productService.canDeleteProducts()) {
            Button delete = new Button("Delete", event -> confirmDelete(product));
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            layout.add(delete);
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
            if (!event.isOpened() && dirty) {
                sidebar.open();
                dirtyDialog.open();
            }
        });

        configureFields();
        bindForm();

        var form = new FormLayout();
        form.add(sku, name, description, unitPrice, quantityOnHand, minimumStock, category, supplier, active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        save.addClickListener(event -> saveProduct());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());

        var footer = new HorizontalLayout(save, cancel, close);
        footer.addClassName("sidebar-footer");

        var content = new VerticalLayout(sidebarTitle, form, footer);
        content.addClassName("sidebar-content");
        sidebar.add(content);
    }

    private void configureFields() {
        sku.setRequiredIndicatorVisible(true);
        sku.setValueChangeMode(ValueChangeMode.EAGER);
        name.setRequiredIndicatorVisible(true);
        name.setValueChangeMode(ValueChangeMode.EAGER);
        description.setMaxLength(1000);
        description.setValueChangeMode(ValueChangeMode.EAGER);
        unitPrice.setRequiredIndicatorVisible(true);
        unitPrice.setValueChangeMode(ValueChangeMode.EAGER);
        quantityOnHand.setRequiredIndicatorVisible(true);
        quantityOnHand.setValueChangeMode(ValueChangeMode.EAGER);
        quantityOnHand.setMin(0);
        minimumStock.setRequiredIndicatorVisible(true);
        minimumStock.setValueChangeMode(ValueChangeMode.EAGER);
        minimumStock.setMin(0);
        category.setItems(categories);
        category.setItemLabelGenerator(Category::getName);
        category.setRequiredIndicatorVisible(true);
        supplier.setItems(suppliers);
        supplier.setItemLabelGenerator(Supplier::getName);
        supplier.setClearButtonVisible(true);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(sku).asRequired("SKU is required.").bind(ProductRequest::getSku, ProductRequest::setSku);
        binder.forField(name).asRequired("Product name is required.").bind(ProductRequest::getName, ProductRequest::setName);
        binder.bind(description, ProductRequest::getDescription, ProductRequest::setDescription);
        binder.forField(unitPrice)
                .asRequired("Unit price is required.")
                .withValidator(value -> value.compareTo(BigDecimal.ZERO) >= 0, "Unit price must be a positive number.")
                .bind(ProductRequest::getUnitPrice, ProductRequest::setUnitPrice);
        binder.forField(quantityOnHand)
                .asRequired("Quantity on hand is required.")
                .withValidator(value -> value >= 0, "Quantity on hand must be zero or greater.")
                .bind(ProductRequest::getQuantityOnHand, ProductRequest::setQuantityOnHand);
        binder.forField(minimumStock)
                .asRequired("Minimum stock is required.")
                .withValidator(value -> value >= 0, "Minimum stock must be zero or greater.")
                .bind(ProductRequest::getMinimumStock, ProductRequest::setMinimumStock);
        binder.forField(category)
                .asRequired("Category is required.")
                .bind(this::categoryFromRequest, (request, value) -> request.setCategoryId(value == null ? null : value.getId()));
        binder.forField(supplier)
                .bind(this::supplierFromRequest, (request, value) -> request.setSupplierId(value == null ? null : value.getId()));
        binder.bind(active, ProductRequest::isActive, ProductRequest::setActive);
    }

    private void configureDialogs() {
        var deleteTitle = new H1("Delete product?");
        var deleteText = new Span("Are you sure you want to delete this product?");
        Button confirm = new Button("Delete", event -> deleteSelected());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancelDelete = new Button("Cancel", event -> deleteDialog.close());
        deleteDialog.add(new VerticalLayout(deleteTitle, deleteText, new HorizontalLayout(confirm, cancelDelete)));

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
        selectedProduct = null;
        sidebarTitle.setText("New Product");
        resetForm(new ProductRequest());
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openEdit(Product product) {
        selectedProduct = productService.get(product.getId());
        mode = FormMode.EDIT;
        sidebarTitle.setText("Edit Product");
        resetForm(fromProduct(selectedProduct));
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openView(Product product) {
        selectedProduct = productService.get(product.getId());
        mode = FormMode.VIEW;
        sidebarTitle.setText("Product Details");
        resetForm(fromProduct(selectedProduct));
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        dirty = false;
        sidebar.open();
    }

    private void resetForm(ProductRequest request) {
        formData.setSku(request.getSku());
        formData.setName(request.getName());
        formData.setDescription(request.getDescription());
        formData.setUnitPrice(request.getUnitPrice() == null ? BigDecimal.ZERO : request.getUnitPrice());
        formData.setQuantityOnHand(request.getQuantityOnHand() == null ? 0 : request.getQuantityOnHand());
        formData.setMinimumStock(request.getMinimumStock() == null ? 0 : request.getMinimumStock());
        formData.setCategoryId(request.getCategoryId());
        formData.setSupplierId(request.getSupplierId());
        formData.setActive(request.isActive());
        formData.setVersion(request.getVersion());
        binder.readBean(formData);
    }

    private void saveProduct() {
        if (!binder.writeBeanIfValid(formData)) {
            showError("Please fix the highlighted fields.");
            return;
        }
        try {
            if (mode == FormMode.CREATE) {
                productService.create(formData);
                showSuccess("Product created.");
            } else if (mode == FormMode.EDIT && selectedProduct != null) {
                productService.update(selectedProduct.getId(), formData);
                showSuccess("Product updated.");
            }
            dirty = false;
            sidebar.close();
            refreshGrid();
        } catch (ProductException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDelete(Product product) {
        selectedProduct = product;
        deleteDialog.open();
    }

    private void deleteSelected() {
        try {
            productService.delete(selectedProduct.getId());
            deleteDialog.close();
            showSuccess("Product removed.");
            refreshGrid();
        } catch (ProductException | AccessDeniedException exception) {
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
        sku.setReadOnly(readOnly);
        name.setReadOnly(readOnly);
        description.setReadOnly(readOnly);
        unitPrice.setReadOnly(readOnly);
        quantityOnHand.setReadOnly(readOnly);
        minimumStock.setReadOnly(readOnly);
        category.setReadOnly(readOnly);
        supplier.setReadOnly(readOnly);
        active.setReadOnly(readOnly);
    }

    private ProductRequest fromProduct(Product product) {
        var request = new ProductRequest();
        request.setSku(product.getSku());
        request.setName(product.getName());
        request.setDescription(product.getDescription());
        request.setUnitPrice(product.getUnitPrice());
        request.setQuantityOnHand(product.getQuantityOnHand());
        request.setMinimumStock(product.getMinimumStock());
        request.setCategoryId(product.getCategory().getId());
        request.setSupplierId(product.getSupplier() == null ? null : product.getSupplier().getId());
        request.setActive(product.isActive());
        request.setVersion(product.getVersion());
        return request;
    }

    private Category categoryFromRequest(ProductRequest request) {
        return categories.stream()
                .filter(item -> item.getId().equals(request.getCategoryId()))
                .findFirst()
                .orElse(null);
    }

    private Supplier supplierFromRequest(ProductRequest request) {
        return suppliers.stream()
                .filter(item -> item.getId().equals(request.getSupplierId()))
                .findFirst()
                .orElse(null);
    }

    private void refreshGrid() {
        grid.setItems(productService.search(new ProductFilter(
                search.getValue(),
                categoryFilter.getValue() == null ? null : categoryFilter.getValue().getId(),
                supplierFilter.getValue() == null ? null : supplierFilter.getValue().getId(),
                activeFilterValue(),
                lowStockFilter.getValue())));
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
