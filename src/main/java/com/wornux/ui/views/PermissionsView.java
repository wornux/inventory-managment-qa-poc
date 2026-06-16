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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.user.Permission;
import com.wornux.user.PermissionAction;
import com.wornux.user.PermissionException;
import com.wornux.user.PermissionFilter;
import com.wornux.user.PermissionRequest;
import com.wornux.user.PermissionService;
import com.wornux.user.ProtectedResource;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

@Route("permissions")
@PageTitle("Permissions")
@PermitAll
public class PermissionsView extends Main {

    private enum FormMode {
        CREATE, EDIT, VIEW
    }

    private final PermissionService permissionService;
    private final Grid<Permission> grid = new Grid<>(Permission.class, false);
    private final ComboBox<ProtectedResource> resourceFilter = new ComboBox<>("Resource");
    private final ComboBox<PermissionAction> actionFilter = new ComboBox<>("Action");
    private final ComboBox<String> activeFilter = new ComboBox<>("Status");
    private final Dialog sidebar = new Dialog();
    private final Dialog deactivateDialog = new Dialog();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<PermissionRequest> binder = new BeanValidationBinder<>(PermissionRequest.class);
    private final PermissionRequest formData = new PermissionRequest();
    private final ComboBox<ProtectedResource> resource = new ComboBox<>("Resource");
    private final ComboBox<PermissionAction> action = new ComboBox<>("Action");
    private final TextArea description = new TextArea("Description");
    private final Checkbox active = new Checkbox("Active");
    private final H1 sidebarTitle = new H1();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private final Button edit = new Button("Edit");
    private final Span deactivateText = new Span();
    private List<ProtectedResource> resources = new ArrayList<>();
    private List<PermissionAction> actions = new ArrayList<>();
    private Permission selectedPermission;
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;

    public PermissionsView(PermissionService permissionService) {
        this.permissionService = permissionService;
        addClassName("products-view");
        resources = permissionService.activeResources();
        actions = permissionService.activeActions();
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
        var title = new H1("Permissions");
        var subtitle = new Span("Resource-action permissions, role usage, and authorization controls.");
        subtitle.addClassName("products-subtitle");
        header.add(title, subtitle);
        return header;
    }

    private Component buildToolbar() {
        var toolbar = new HorizontalLayout();
        toolbar.addClassName("products-toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.END);

        Button newPermission = new Button("New Permission", event -> openCreate());
        newPermission.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newPermission.setVisible(permissionService.canManagePermissions());

        toolbar.add(resourceFilter, actionFilter, activeFilter, newPermission);
        toolbar.setFlexGrow(1, resourceFilter);
        return toolbar;
    }

    private void configureFilters() {
        configureResourceCombo(resourceFilter, true);
        configureActionCombo(actionFilter, true);
        resourceFilter.setPlaceholder("All resources");
        actionFilter.setPlaceholder("All actions");
        resourceFilter.addValueChangeListener(event -> refreshGrid());
        actionFilter.addValueChangeListener(event -> refreshGrid());

        activeFilter.setItems("Active", "Inactive", "All");
        activeFilter.setValue("Active");
        activeFilter.setClearButtonVisible(false);
        activeFilter.addValueChangeListener(event -> refreshGrid());
    }

    private void configureGrid() {
        grid.addClassName("products-grid");
        grid.setSizeFull();
        grid.addColumn(permissionRenderer()).setHeader("Permission").setSortable(true).setAutoWidth(true).setFlexGrow(2);
        grid.addColumn(permission -> permission.getResource().getName()).setHeader("Resource").setAutoWidth(true);
        grid.addColumn(permission -> permission.getAction().getName()).setHeader("Action").setAutoWidth(true);
        grid.addColumn(Permission::getDescription).setHeader("Description").setAutoWidth(true).setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(this::activeBadge)).setHeader("Active").setAutoWidth(true);
        grid.addColumn(permission -> permissionService.roleCount(permission.getId())).setHeader("Role Count").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::actions)).setHeader("Actions").setAutoWidth(true);
        grid.addItemClickListener(event -> openView(event.getItem()));
    }

    private LitRenderer<Permission> permissionRenderer() {
        return LitRenderer.<Permission>of("""
                <div class="product-cell">
                  <strong>${item.code}</strong>
                  <span>${item.label}</span>
                </div>
                """)
                .withProperty("code", Permission::getCode)
                .withProperty("label", permission -> permission.getResource().getName() + " / " + permission.getAction().getName());
    }

    private Component activeBadge(Permission permission) {
        Span badge = new Span(permission.isActive() ? "Active" : "Inactive");
        badge.addClassNames("status-badge", permission.isActive() ? "active-yes" : "active-no");
        return badge;
    }

    private Component actions(Permission permission) {
        var layout = new HorizontalLayout();
        layout.addClassName("row-actions");
        layout.add(new Button("View", event -> openView(permission)));
        if (permissionService.canManagePermissions()) {
            layout.add(new Button("Edit", event -> openEdit(permission)));
            Button deactivate = new Button("Deactivate", event -> confirmDeactivate(permission));
            deactivate.addThemeVariants(ButtonVariant.LUMO_ERROR);
            deactivate.setVisible(permission.isActive());
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
        form.add(resource, action, description, active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        save.addClickListener(event -> savePermission());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());
        edit.addClickListener(event -> {
            if (selectedPermission != null) {
                openEdit(selectedPermission);
            }
        });

        var footer = new HorizontalLayout(save, cancel, close, edit);
        footer.addClassName("sidebar-footer");

        var content = new VerticalLayout(sidebarTitle, form, footer);
        content.addClassName("sidebar-content");
        sidebar.add(content);
    }

    private void configureFields() {
        configureResourceCombo(resource, false);
        configureActionCombo(action, false);
        resource.setRequiredIndicatorVisible(true);
        action.setRequiredIndicatorVisible(true);
        description.setMaxLength(500);
        active.setValue(true);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void configureResourceCombo(ComboBox<ProtectedResource> combo, boolean clearable) {
        combo.setItems(resources);
        combo.setItemLabelGenerator(item -> item.getCode() + " - " + item.getName());
        combo.setClearButtonVisible(clearable);
    }

    private void configureActionCombo(ComboBox<PermissionAction> combo, boolean clearable) {
        combo.setItems(actions);
        combo.setItemLabelGenerator(item -> item.getCode() + " - " + item.getName());
        combo.setClearButtonVisible(clearable);
    }

    private void bindForm() {
        binder.forField(resource)
                .asRequired("Resource is required.")
                .bind(this::resourceFromRequest, (request, value) -> request.setResourceId(value == null ? null : value.getId()));
        binder.forField(action)
                .asRequired("Action is required.")
                .bind(this::actionFromRequest, (request, value) -> request.setActionId(value == null ? null : value.getId()));
        binder.bind(description, PermissionRequest::getDescription, PermissionRequest::setDescription);
        binder.bind(active, PermissionRequest::isActive, PermissionRequest::setActive);
    }

    private void configureDialogs() {
        Button confirm = new Button("Deactivate", event -> deactivateSelected());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancelDeactivate = new Button("Cancel", event -> deactivateDialog.close());
        deactivateDialog.add(new VerticalLayout(
                new H1("Deactivate this permission?"),
                deactivateText,
                new HorizontalLayout(confirm, cancelDeactivate)));

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
        refreshOptions();
        mode = FormMode.CREATE;
        selectedPermission = null;
        sidebarTitle.setText("New Permission");
        resetForm(new PermissionRequest());
        setReadOnly(false);
        resource.setReadOnly(false);
        action.setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openEdit(Permission permission) {
        selectedPermission = permissionService.get(permission.getId());
        mode = FormMode.EDIT;
        sidebarTitle.setText("Edit Permission");
        resetForm(fromPermission(selectedPermission));
        if (!selectedPermission.getResource().isActive() || !selectedPermission.getAction().isActive()) {
            showWarning("The resource and/or action for this permission is no longer active.");
        }
        setReadOnly(false);
        resource.setReadOnly(true);
        action.setReadOnly(true);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openView(Permission permission) {
        selectedPermission = permissionService.get(permission.getId());
        mode = FormMode.VIEW;
        sidebarTitle.setText("Permission Details");
        resetForm(fromPermission(selectedPermission));
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        edit.setVisible(permissionService.canManagePermissions());
        dirty = false;
        sidebar.open();
    }

    private PermissionRequest fromPermission(Permission permission) {
        var request = new PermissionRequest();
        request.setResourceId(permission.getResource().getId());
        request.setActionId(permission.getAction().getId());
        request.setDescription(permission.getDescription());
        request.setActive(permission.isActive());
        request.setVersion(permission.getVersion());
        return request;
    }

    private void resetForm(PermissionRequest request) {
        formData.setResourceId(request.getResourceId());
        formData.setActionId(request.getActionId());
        formData.setDescription(request.getDescription());
        formData.setActive(request.isActive());
        formData.setVersion(request.getVersion());
        binder.readBean(formData);
        resource.setInvalid(false);
        action.setInvalid(false);
        description.setInvalid(false);
    }

    private void savePermission() {
        if (!binder.writeBeanIfValid(formData)) {
            showError("Please fix the highlighted fields.");
            return;
        }
        try {
            if (mode == FormMode.CREATE) {
                permissionService.create(formData);
                showSuccess("Permission created.");
            } else if (mode == FormMode.EDIT && selectedPermission != null) {
                permissionService.update(selectedPermission.getId(), formData);
                showSuccess("Permission updated.");
            }
            dirty = false;
            sidebar.close();
            refreshGrid();
        } catch (PermissionException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDeactivate(Permission permission) {
        selectedPermission = permission;
        long roles = permissionService.roleCount(permission.getId());
        if (roles > 0) {
            deactivateText.setText("This permission has " + roles
                    + " role assignments. Deactivating it will not remove existing assignments, but new roles cannot be assigned this permission.");
        } else {
            deactivateText.setText("Roles that have this permission will be unaffected, but new roles cannot be assigned this permission.");
        }
        deactivateDialog.open();
    }

    private void deactivateSelected() {
        try {
            permissionService.deactivate(selectedPermission.getId());
            deactivateDialog.close();
            dirty = false;
            sidebar.close();
            showSuccess("Permission deactivated.");
            refreshGrid();
        } catch (PermissionException | AccessDeniedException exception) {
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
        resource.setReadOnly(readOnly);
        action.setReadOnly(readOnly);
        description.setReadOnly(readOnly);
        active.setReadOnly(readOnly);
    }

    private ProtectedResource resourceFromRequest(PermissionRequest request) {
        return resources.stream()
                .filter(item -> item.getId().equals(request.getResourceId()))
                .findFirst()
                .orElse(null);
    }

    private PermissionAction actionFromRequest(PermissionRequest request) {
        return actions.stream()
                .filter(item -> item.getId().equals(request.getActionId()))
                .findFirst()
                .orElse(null);
    }

    private void refreshOptions() {
        resources = permissionService.activeResources();
        actions = permissionService.activeActions();
        resource.setItems(resources);
        action.setItems(actions);
        resourceFilter.setItems(resources);
        actionFilter.setItems(actions);
    }

    private void refreshGrid() {
        grid.setItems(permissionService.search(new PermissionFilter(
                resourceFilter.getValue() == null ? null : resourceFilter.getValue().getId(),
                actionFilter.getValue() == null ? null : actionFilter.getValue().getId(),
                activeFilterValue())));
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

    private void showWarning(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    private void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
