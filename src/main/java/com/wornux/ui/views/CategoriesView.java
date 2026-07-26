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
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.dataview.GridLazyDataView;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.masterdetaillayout.MasterDetailLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
import com.wornux.catalog.CategoryFilter;
import com.wornux.catalog.CategoryRequest;
import com.wornux.catalog.CategoryService;
import jakarta.annotation.security.PermitAll;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

@Route("categories")
@PageTitle("Categories")
@PermitAll
public class CategoriesView extends MasterDetailLayout {

    private enum FormMode {
        CREATE,
        EDIT,
        VIEW
    }

    private final CategoryService categoryService;
    private final Grid<Category> grid = new Grid<>(Category.class, false);
    private final TextField search = new TextField();
    private final ComboBox<String> activeFilter = new ComboBox<>("Status");
    private final ValueSignal<String> searchSignal = new ValueSignal<>("");
    private final ValueSignal<String> activeStatusSignal = new ValueSignal<>("Active");
    private final Signal<CategoryFilter> filterSignal =
            Signal.computed(() -> new CategoryFilter(searchSignal.get(), activeFilterValue(activeStatusSignal.get())));
    private final VerticalLayout detailContent = new VerticalLayout();
    private final Dialog deactivateDialog = new Dialog();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<CategoryRequest> binder = new BeanValidationBinder<>(CategoryRequest.class);
    private final CategoryRequest formData = new CategoryRequest();
    private final TextField name = new TextField("Name");
    private final TextArea description = new TextArea("Description");
    private final Checkbox active = new Checkbox("Active");
    private final H2 detailTitle = new H2();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private final Button edit = new Button("Edit");
    private final H1 deactivateTitle = new H1("Deactivate this category?");
    private final Span deactivateText = new Span();
    private Category selectedCategory;
    private Category deactivateTarget;
    private GridLazyDataView<Category> gridDataView;
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;
    private Runnable deferredAction;

    public CategoriesView(CategoryService categoryService) {
        this.categoryService = categoryService;
        setSizeFull();
        addClassName("crud-master-detail");
        setExpandMaster(true);
        setMasterSize("32rem");
        setDetailSize("30rem");
        setOverlaySize("min(30rem, 100%)");
        configureFilters();
        configureGrid();
        gridDataView = PageableGridBinding.bind(grid, filterSignal, categoryService::search);
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
        var title = new H1("Categories");
        var subtitle = new Span("Product grouping, assignment availability, and catalog organization.");
        subtitle.addClassName("products-subtitle");
        header.add(title, subtitle);

        return header;
    }

    private Component buildToolbar() {
        var toolbar = new HorizontalLayout();
        toolbar.addClassName("products-toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.END);

        Button newCategory = new Button("New Category", event -> requestSwitch(this::openCreate));
        newCategory.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newCategory.setVisible(categoryService.canCreateCategories());

        toolbar.add(search, activeFilter, newCategory);
        toolbar.setFlexGrow(1, search);

        return toolbar;
    }

    private void configureFilters() {
        search.setPlaceholder("Search category name");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.bindValue(searchSignal, searchSignal::set);

        activeFilter.setItems("Active", "Inactive", "All");
        activeFilter.setClearButtonVisible(false);
        activeFilter.bindValue(activeStatusSignal, activeStatusSignal::set);
    }

    private void configureGrid() {
        grid.addClassName("products-grid");
        grid.setSizeFull();
        var categoryColumn = grid.addColumn(categoryRenderer())
                .setHeader("Category")
                .setSortProperty("name", "id")
                .setAutoWidth(true)
                .setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(this::activeBadge))
                .setHeader("Active")
                .setAutoWidth(true);
        grid.addColumn(category -> categoryService.productCount(category.getId()))
                .setHeader("Product Count")
                .setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::actions))
                .setHeader("Actions")
                .setAutoWidth(true);
        grid.addItemClickListener(event -> requestSwitch(() -> openView(event.getItem())));
        grid.sort(List.of(new GridSortOrder<>(categoryColumn, SortDirection.ASCENDING)));
    }

    private LitRenderer<Category> categoryRenderer() {
        return LitRenderer.<Category>of("""
                <div class="product-cell">
                  <strong>${item.name}</strong>
                  <span>${item.description}</span>
                </div>
                """)
                .withProperty("name", Category::getName)
                .withProperty(
                        "description",
                        category -> category.getDescription() == null ? "No description" : category.getDescription());
    }

    private Component activeBadge(Category category) {
        Span badge = new Span(category.isActive() ? "Active" : "Inactive");
        badge.addClassNames("status-badge", category.isActive() ? "active-yes" : "active-no");

        return badge;
    }

    private Component actions(Category category) {
        var layout = new HorizontalLayout();
        layout.addClassName("row-actions");
        Button view = new Button("View", event -> requestSwitch(() -> openView(category)));
        layout.add(view);

        if (categoryService.canUpdateCategories()) {
            layout.add(new Button("Edit", event -> requestSwitch(() -> openEdit(category))));
        }

        if (categoryService.canDeleteCategories() && category.isActive()) {
            Button deactivate =
                    new Button("Deactivate", event -> requestDestructive(() -> confirmDeactivate(category)));
            deactivate.addThemeVariants(ButtonVariant.LUMO_ERROR);
            layout.add(deactivate);
        }

        return layout;
    }

    private void configureDetail() {
        configureFields();
        bindForm();

        var form = new FormLayout();
        form.add(name, description, active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        save.addClickListener(event -> saveCategory());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());
        edit.addClickListener(event -> {
            if (selectedCategory != null) {
                requestSwitch(() -> openEdit(selectedCategory));
            }
        });

        var footer = new HorizontalLayout(save, cancel, close, edit);
        footer.addClassName("crud-detail-footer");

        detailTitle.setId("category-detail-title");
        detailContent.add(detailTitle, form, footer);
        detailContent.addClassName("crud-detail-content");
        detailContent.getElement().setAttribute("aria-labelledby", "category-detail-title");
        detailContent.getElement().setAttribute("role", "region");
    }

    private void configureFields() {
        name.setRequiredIndicatorVisible(true);
        name.setValueChangeMode(ValueChangeMode.EAGER);
        description.setMaxLength(500);
        description.setValueChangeMode(ValueChangeMode.EAGER);
        active.setValue(true);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(name)
                .asRequired("Category name is required.")
                .bind(CategoryRequest::getName, CategoryRequest::setName);
        binder.bind(description, CategoryRequest::getDescription, CategoryRequest::setDescription);
        binder.bind(active, CategoryRequest::isActive, CategoryRequest::setActive);
    }

    private void configureDialogs() {
        Button confirm = new Button("Deactivate", event -> deactivateSelected());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancelDeactivate = new Button("Cancel", event -> {
            deactivateTarget = null;
            deactivateDialog.close();
        });
        deactivateDialog.add(
                new VerticalLayout(deactivateTitle, deactivateText, new HorizontalLayout(confirm, cancelDeactivate)));

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

    private void openCreate() {
        mode = FormMode.CREATE;
        selectedCategory = null;
        detailTitle.setText("New Category");
        resetForm(new CategoryRequest());
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        setDetail(detailContent);
    }

    private void openEdit(Category category) {
        selectedCategory = categoryService.get(category.getId());
        mode = FormMode.EDIT;
        detailTitle.setText("Edit Category");
        resetForm(fromCategory(selectedCategory));
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        setDetail(detailContent);
    }

    private void openView(Category category) {
        selectedCategory = categoryService.get(category.getId());
        mode = FormMode.VIEW;
        detailTitle.setText("Category Details");
        resetForm(fromCategory(selectedCategory));
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        edit.setVisible(categoryService.canUpdateCategories());
        dirty = false;
        setDetail(detailContent);
    }

    private void resetForm(CategoryRequest request) {
        formData.setName(request.getName());
        formData.setDescription(request.getDescription());
        formData.setActive(request.isActive());
        formData.setVersion(request.getVersion());
        binder.readBean(formData);
        name.setInvalid(false);
        description.setInvalid(false);
    }

    private CategoryRequest fromCategory(Category category) {
        var request = new CategoryRequest();
        request.setName(category.getName());
        request.setDescription(category.getDescription());
        request.setActive(category.isActive());
        request.setVersion(category.getVersion());

        return request;
    }

    private void saveCategory() {
        if (!binder.writeBeanIfValid(formData)) {
            showError("Please fix the highlighted fields.");
            return;
        }

        try {
            if (mode == FormMode.CREATE) {
                categoryService.create(formData);
                showSuccess("Category created.");
            } else if (mode == FormMode.EDIT && selectedCategory != null) {
                categoryService.update(selectedCategory.getId(), formData);
                showSuccess("Category updated.");
            }

            dirty = false;
            setDetail(null);
            refreshGrid();
        } catch (CategoryException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDeactivate(Category category) {
        deactivateTarget = category;
        long activeProducts = categoryService.activeProductCount(category.getId());

        if (activeProducts > 0) {
            deactivateText.setText(
                    "This category has " + activeProducts
                            + " products. Deactivating the category will not affect existing products, but new products cannot be assigned to it.");
        } else {
            deactivateText.setText(
                    "Products in this category will still exist but will be hidden from new assignments.");
        }

        deactivateDialog.open();
    }

    private void deactivateSelected() {
        try {
            categoryService.deactivate(deactivateTarget.getId());
            deactivateDialog.close();
            deactivateTarget = null;
            dirty = false;
            setDetail(null);
            showSuccess("Category deactivated.");
            refreshGrid();
        } catch (CategoryException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void requestDestructive(Runnable action) {
        boolean discardDetail = dirty && mode != FormMode.VIEW;
        requestSwitch(() -> {
            if (discardDetail) {
                selectedCategory = null;
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
        name.setReadOnly(readOnly);
        description.setReadOnly(readOnly);
        active.setReadOnly(readOnly);
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
