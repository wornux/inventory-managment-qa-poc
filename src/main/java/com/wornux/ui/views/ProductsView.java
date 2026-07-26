package com.wornux.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.dataview.GridLazyDataView;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.masterdetaillayout.MasterDetailLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryException;
import com.wornux.catalog.CategoryRequest;
import com.wornux.catalog.CategoryService;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductException;
import com.wornux.catalog.ProductFilter;
import com.wornux.catalog.ProductRequest;
import com.wornux.catalog.ProductService;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierException;
import com.wornux.catalog.SupplierRequest;
import com.wornux.catalog.SupplierService;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

@Route("products")
@PageTitle("Products")
@PermitAll
public class ProductsView extends MasterDetailLayout {

    private enum FormMode {
        CREATE,
        EDIT,
        VIEW
    }

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SupplierService supplierService;
    private final Grid<Product> grid = new Grid<>(Product.class, false);
    private final TextField search = new TextField();
    private final ComboBox<Category> categoryFilter = new ComboBox<>("Category");
    private final ComboBox<Supplier> supplierFilter = new ComboBox<>("Supplier");
    private final ComboBox<String> activeFilter = new ComboBox<>("Status");
    private final Checkbox lowStockFilter = new Checkbox("Low stock");
    private final ValueSignal<String> searchSignal = new ValueSignal<>("");
    private final ValueSignal<Category> categoryFilterSignal = new ValueSignal<>(null);
    private final ValueSignal<Supplier> supplierFilterSignal = new ValueSignal<>(null);
    private final ValueSignal<String> activeStatusSignal = new ValueSignal<>("Active");
    private final ValueSignal<Boolean> lowStockSignal = new ValueSignal<>(false);
    private final Signal<ProductFilter> filterSignal = Signal.computed(() -> new ProductFilter(
            searchSignal.get(),
            categoryFilterSignal.get() == null
                    ? null
                    : categoryFilterSignal.get().getId(),
            supplierFilterSignal.get() == null
                    ? null
                    : supplierFilterSignal.get().getId(),
            activeFilterValue(activeStatusSignal.get()),
            lowStockSignal.get()));
    private final VerticalLayout detailContent = new VerticalLayout();
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
    private final Button createCategory =
            new Button(new SvgIcon("/icons/dependency-create.svg"), event -> openCategoryCreateDialog());
    private final ComboBox<Supplier> supplier = new ComboBox<>("Supplier");
    private final Button createSupplier =
            new Button(new SvgIcon("/icons/dependency-create.svg"), event -> openSupplierCreateDialog());
    private final Checkbox active = new Checkbox("Active");
    private final H2 detailTitle = new H2();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private List<Category> categories = new ArrayList<>();
    private List<Supplier> suppliers = new ArrayList<>();
    private Product selectedProduct;
    private Product deleteTarget;
    private GridLazyDataView<Product> gridDataView;
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;
    private Runnable deferredAction;

    public ProductsView(
            ProductService productService, CategoryService categoryService, SupplierService supplierService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.supplierService = supplierService;
        setId("products-view");
        setSizeFull();
        addClassName("crud-master-detail");
        setExpandMaster(true);
        setMasterSize("44rem");
        setDetailSize("30rem");
        setOverlaySize("min(30rem, 100%)");
        categories = productService.activeCategories();
        suppliers = productService.activeSuppliers();
        configureFilters();
        configureGrid();
        gridDataView = PageableGridBinding.bind(grid, filterSignal, productService::search);
        configureDetail();
        configureDialogs();

        var master = new Main();
        master.setSizeFull();
        master.addClassName("products-view");
        master.add(buildHeader(), buildToolbar(), grid);
        setMaster(master);
        addBackdropClickListener(event -> requestClose());
        addDetailEscapePressListener(event -> requestClose());
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

        Button newProduct = new Button(
                "New Product", new SvgIcon("/icons/grid-create.svg"), event -> requestSwitch(this::openCreate));
        newProduct.setId("new-product");
        newProduct.setAriaLabel("New Product");
        newProduct.setTooltipText("New Product");
        newProduct.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newProduct.setVisible(productService.canCreateProducts());

        toolbar.add(search, categoryFilter, supplierFilter, activeFilter, lowStockFilter, newProduct);
        toolbar.setFlexGrow(1, search);

        return toolbar;
    }

    private void configureFilters() {
        search.setId("product-search");
        search.setPlaceholder("Search SKU or name");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.bindValue(searchSignal, searchSignal::set);

        categoryFilter.setId("product-category-filter");
        categoryFilter.setItems(categories);
        categoryFilter.setItemLabelGenerator(Category::getName);
        categoryFilter.setClearButtonVisible(true);
        categoryFilter.bindValue(categoryFilterSignal, categoryFilterSignal::set);

        supplierFilter.setId("product-supplier-filter");
        supplierFilter.setItems(suppliers);
        supplierFilter.setItemLabelGenerator(Supplier::getName);
        supplierFilter.setClearButtonVisible(true);
        supplierFilter.bindValue(supplierFilterSignal, supplierFilterSignal::set);

        activeFilter.setId("product-status-filter");
        activeFilter.setItems("Active", "Inactive", "All");
        activeFilter.setClearButtonVisible(false);
        activeFilter.bindValue(activeStatusSignal, activeStatusSignal::set);

        lowStockFilter.setId("product-low-stock-filter");
        lowStockFilter.bindValue(lowStockSignal, lowStockSignal::set);
    }

    private void configureGrid() {
        grid.setId("products-grid");
        grid.addClassName("products-grid");
        grid.setSizeFull();
        var productColumn = grid.addColumn(productRenderer())
                .setHeader("Product")
                .setSortProperty("sku", "id")
                .setAutoWidth(true)
                .setFlexGrow(2);
        grid.addColumn(product -> product.getCategory().getName())
                .setHeader("Category")
                .setSortProperty("category.name", "id");
        grid.addColumn(product -> product.getSupplier() == null
                        ? "None"
                        : product.getSupplier().getName())
                .setHeader("Supplier")
                .setSortProperty("supplier.name", "id");
        grid.addColumn(Product::getUnitPrice).setHeader("Unit Price").setSortProperty("unitPrice", "id");
        grid.addColumn(Product::getQuantityOnHand).setHeader("Quantity").setSortProperty("quantityOnHand", "id");
        grid.addColumn(Product::getMinimumStock).setHeader("Minimum").setSortProperty("minimumStock", "id");
        grid.addColumn(new ComponentRenderer<>(this::stockStatusBadge))
                .setHeader("Stock Status")
                .setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::activeBadge))
                .setHeader("Active")
                .setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::actions))
                .setHeader("Actions")
                .setAutoWidth(true);
        grid.addItemClickListener(event -> requestSwitch(() -> openView(event.getItem())));
        grid.sort(List.of(new GridSortOrder<>(productColumn, SortDirection.ASCENDING)));
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
        Button view = new Button("View", event -> requestSwitch(() -> openView(product)));
        layout.add(view);

        if (productService.canUpdateProducts()) {
            layout.add(new Button("Edit", event -> requestSwitch(() -> openEdit(product))));
        }

        if (productService.canDeleteProducts()) {
            Button delete = new Button("Delete", event -> requestDestructive(() -> confirmDelete(product)));
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            layout.add(delete);
        }

        return layout;
    }

    private void configureDetail() {
        configureFields();
        bindForm();

        var categoryAssignment = new HorizontalLayout(category, createCategory);
        categoryAssignment.addClassName("product-dependency-field");
        categoryAssignment.setAlignItems(HorizontalLayout.Alignment.END);
        categoryAssignment.setWidthFull();
        categoryAssignment.setFlexGrow(1, category);

        var supplierAssignment = new HorizontalLayout(supplier, createSupplier);
        supplierAssignment.addClassName("product-dependency-field");
        supplierAssignment.setAlignItems(HorizontalLayout.Alignment.END);
        supplierAssignment.setWidthFull();
        supplierAssignment.setFlexGrow(1, supplier);

        var form = new FormLayout();
        form.add(
                sku,
                name,
                description,
                unitPrice,
                quantityOnHand,
                minimumStock,
                categoryAssignment,
                supplierAssignment,
                active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> saveProduct());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());

        var footer = new HorizontalLayout(save, cancel, close);
        footer.addClassName("crud-detail-footer");

        detailTitle.setId("product-detail-title");
        detailContent.add(detailTitle, form, footer);
        detailContent.addClassName("crud-detail-content");
        detailContent.getElement().setAttribute("aria-labelledby", "product-detail-title");
        detailContent.getElement().setAttribute("role", "region");
    }

    private void configureFields() {
        sku.setId("product-sku");
        sku.setRequiredIndicatorVisible(true);
        sku.setValueChangeMode(ValueChangeMode.EAGER);
        name.setId("product-name");
        name.setRequiredIndicatorVisible(true);
        name.setValueChangeMode(ValueChangeMode.EAGER);
        description.setId("product-description");
        description.setMaxLength(1000);
        description.setValueChangeMode(ValueChangeMode.EAGER);
        unitPrice.setId("product-unit-price");
        unitPrice.setRequiredIndicatorVisible(true);
        unitPrice.setValueChangeMode(ValueChangeMode.EAGER);
        quantityOnHand.setId("product-quantity");
        quantityOnHand.setRequiredIndicatorVisible(true);
        quantityOnHand.setValueChangeMode(ValueChangeMode.EAGER);
        quantityOnHand.setMin(0);
        minimumStock.setId("product-minimum-stock");
        minimumStock.setRequiredIndicatorVisible(true);
        minimumStock.setValueChangeMode(ValueChangeMode.EAGER);
        minimumStock.setMin(0);
        category.setId("product-category");
        category.setItems(categories);
        category.setItemLabelGenerator(Category::getName);
        category.setRequiredIndicatorVisible(true);
        createCategory.setAriaLabel("Create category");
        createCategory.setTooltipText("Create category");
        createCategory.addThemeVariants(ButtonVariant.TERTIARY);
        createCategory.setVisible(false);
        supplier.setId("product-supplier");
        supplier.setItems(suppliers);
        supplier.setItemLabelGenerator(Supplier::getName);
        supplier.setClearButtonVisible(true);
        createSupplier.setAriaLabel("Create supplier");
        createSupplier.setTooltipText("Create supplier");
        createSupplier.addThemeVariants(ButtonVariant.TERTIARY);
        createSupplier.setVisible(false);
        active.setId("product-active");
        save.setId("save-product");
        cancel.setId("cancel-product");
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(sku).asRequired("SKU is required.").bind(ProductRequest::getSku, ProductRequest::setSku);
        binder.forField(name)
                .asRequired("Product name is required.")
                .bind(ProductRequest::getName, ProductRequest::setName);
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
                .bind(
                        this::categoryFromRequest,
                        (request, value) -> request.setCategoryId(value == null ? null : value.getId()));
        binder.forField(supplier)
                .bind(
                        this::supplierFromRequest,
                        (request, value) -> request.setSupplierId(value == null ? null : value.getId()));
        binder.bind(active, ProductRequest::isActive, ProductRequest::setActive);
    }

    private void configureDialogs() {
        var deleteTitle = new H1("Delete product?");
        var deleteText = new Span("Are you sure you want to delete this product?");
        Button confirm = new Button("Delete", event -> deleteSelected());
        confirm.setId("confirm-product-delete");
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancelDelete = new Button("Cancel", event -> {
            deleteTarget = null;
            deleteDialog.close();
        });
        deleteDialog.add(new VerticalLayout(deleteTitle, deleteText, new HorizontalLayout(confirm, cancelDelete)));

        var dirtyTitle = new H1("Unsaved changes");
        var dirtyText = new Span("You have unsaved changes. Discard them?");
        Button discard = new Button("Discard", event -> {
            dirty = false;
            dirtyDialog.close();
            if (deferredAction != null) {
                Runnable action = deferredAction;
                deferredAction = null;
                action.run();
                return;
            }

            setDetail(null);
        });
        discard.addThemeVariants(ButtonVariant.LUMO_ERROR);
        Button stay = new Button("Cancel", event -> {
            deferredAction = null;
            dirtyDialog.close();
        });
        dirtyDialog.add(new VerticalLayout(dirtyTitle, dirtyText, new HorizontalLayout(discard, stay)));
    }

    private void openCategoryCreateDialog() {
        var dialog = new Dialog();
        dialog.addClassName("dependency-create-dialog");
        dialog.getElement().setProperty("ariaLabel", "Create category");

        var chip = new Span("catalog.category.create");
        chip.addClassName("dependency-dialog-chip");
        var heading = new H2("Create category");
        var description = new Span("Add a category without leaving the product form.");
        description.addClassName("dependency-dialog-description");
        var nameField = new TextField("Name");
        nameField.setWidthFull();

        var request = new CategoryRequest();
        var dialogBinder = new BeanValidationBinder<>(CategoryRequest.class);
        dialogBinder.bind(nameField, "name");

        var content = new VerticalLayout(chip, heading, description, nameField);
        content.addClassName("dependency-dialog-content");
        content.setPadding(false);
        content.setSpacing(false);
        dialog.add(content);

        var cancelButton = new Button("Cancel", event -> dialog.close());
        cancelButton.addClassName("dependency-dialog-action");
        cancelButton.addThemeVariants(ButtonVariant.TERTIARY);
        var createButton = new Button("Create", event -> {
            if (!dialogBinder.writeBeanIfValid(request)) {
                return;
            }

            try {
                Category created = categoryService.create(request);
                Category filteredCategory = categoryFilter.getValue();
                categories = new ArrayList<>(categories);
                categories.removeIf(item -> created.getId().equals(item.getId()));
                categories.add(created);
                categories.sort(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER));
                category.setItems(categories);
                categoryFilter.setItems(categories);
                categoryFilter.setValue(filteredCategory);
                category.setValue(created);
                dialog.close();
            } catch (CategoryException | AccessDeniedException exception) {
                showError(exception.getMessage());
            }
        });
        createButton.addClassName("dependency-dialog-action");
        createButton.addThemeVariants(ButtonVariant.PRIMARY);
        dialog.getFooter().add(cancelButton, createButton);
        dialog.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                nameField.focus();
            }
        });
        dialog.open();
    }

    private void openSupplierCreateDialog() {
        var dialog = new Dialog();
        dialog.addClassName("dependency-create-dialog");
        dialog.getElement().setProperty("ariaLabel", "Create supplier");

        var chip = new Span("catalog.supplier.create");
        chip.addClassName("dependency-dialog-chip");
        var heading = new H2("Create supplier");
        var description = new Span("Add a supplier without leaving the product form.");
        description.addClassName("dependency-dialog-description");
        var nameField = new TextField("Name");
        nameField.setWidthFull();

        var request = new SupplierRequest();
        var dialogBinder = new BeanValidationBinder<>(SupplierRequest.class);
        dialogBinder.bind(nameField, "name");

        var content = new VerticalLayout(chip, heading, description, nameField);
        content.addClassName("dependency-dialog-content");
        content.setPadding(false);
        content.setSpacing(false);
        dialog.add(content);

        var cancelButton = new Button("Cancel", event -> dialog.close());
        cancelButton.addClassName("dependency-dialog-action");
        cancelButton.addThemeVariants(ButtonVariant.TERTIARY);
        var createButton = new Button("Create", event -> {
            if (!dialogBinder.writeBeanIfValid(request)) {
                return;
            }

            try {
                Supplier created = supplierService.create(request);
                Supplier filteredSupplier = supplierFilter.getValue();
                suppliers = new ArrayList<>(suppliers);
                suppliers.removeIf(item -> created.getId().equals(item.getId()));
                suppliers.add(created);
                suppliers.sort(Comparator.comparing(Supplier::getName, String.CASE_INSENSITIVE_ORDER));
                supplier.setItems(suppliers);
                supplierFilter.setItems(suppliers);
                supplierFilter.setValue(filteredSupplier);
                supplier.setValue(created);
                dialog.close();
            } catch (SupplierException | AccessDeniedException exception) {
                showError(exception.getMessage());
            }
        });
        createButton.addClassName("dependency-dialog-action");
        createButton.addThemeVariants(ButtonVariant.PRIMARY);
        dialog.getFooter().add(cancelButton, createButton);
        dialog.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                nameField.focus();
            }
        });
        dialog.open();
    }

    private void openCreate() {
        mode = FormMode.CREATE;
        selectedProduct = null;
        detailTitle.setText("New Product");
        resetForm(new ProductRequest());
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        dirty = false;
        setDetail(detailContent);
    }

    private void openEdit(Product product) {
        selectedProduct = productService.get(product.getId());
        mode = FormMode.EDIT;
        detailTitle.setText("Edit Product");
        resetForm(fromProduct(selectedProduct));
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        dirty = false;
        setDetail(detailContent);
    }

    private void openView(Product product) {
        selectedProduct = productService.get(product.getId());
        mode = FormMode.VIEW;
        detailTitle.setText("Product Details");
        resetForm(fromProduct(selectedProduct));
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        dirty = false;
        setDetail(detailContent);
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
            setDetail(null);
            refreshGrid();
        } catch (ProductException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDelete(Product product) {
        deleteTarget = product;
        deleteDialog.open();
    }

    private void deleteSelected() {
        try {
            Long deletedId = deleteTarget.getId();
            boolean deletedDetailOpen =
                    selectedProduct != null && selectedProduct.getId().equals(deletedId);
            productService.delete(deletedId);
            deleteDialog.close();
            deleteTarget = null;

            if (deletedDetailOpen) {
                selectedProduct = null;
                dirty = false;
                setDetail(null);
            }

            showSuccess("Product removed.");
            refreshGrid();
        } catch (ProductException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void requestDestructive(Runnable action) {
        boolean discardDetail = dirty && mode != FormMode.VIEW;
        requestSwitch(() -> {
            if (discardDetail) {
                selectedProduct = null;
                setDetail(null);
            }

            action.run();
        });
    }

    private void requestSwitch(Runnable action) {
        if (dirty && mode != FormMode.VIEW) {
            deferredAction = action;
            dirtyDialog.open();
            return;
        }

        deferredAction = null;
        action.run();
    }

    private void requestClose() {
        deferredAction = null;
        if (dirty && mode != FormMode.VIEW) {
            dirtyDialog.open();
            return;
        }

        dirty = false;
        setDetail(null);
    }

    private void setReadOnly(boolean readOnly) {
        sku.setReadOnly(readOnly);
        name.setReadOnly(readOnly);
        description.setReadOnly(readOnly);
        unitPrice.setReadOnly(readOnly);
        quantityOnHand.setReadOnly(readOnly);
        minimumStock.setReadOnly(readOnly);
        category.setReadOnly(readOnly);
        createCategory.setVisible(!readOnly && categoryService.canCreateCategories());
        supplier.setReadOnly(readOnly);
        createSupplier.setVisible(!readOnly && supplierService.canCreateSuppliers());
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
        request.setSupplierId(
                product.getSupplier() == null ? null : product.getSupplier().getId());
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
        gridDataView.refreshAll();
    }

    private Boolean activeFilterValue(String value) {
        if ("Active".equals(value)) {
            return true;
        }

        if ("Inactive".equals(value)) {
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
