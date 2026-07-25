package com.wornux.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.security.permission.AppPermission;
import com.wornux.user.Role;
import com.wornux.user.RoleException;
import com.wornux.user.RoleFilter;
import com.wornux.user.RoleRequest;
import com.wornux.user.RoleService;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;

@Route("roles")
@PageTitle("Roles")
@PermitAll
public class RolesView extends Main {

    private enum FormMode {
        CREATE, EDIT, VIEW
    }

    private final RoleService roleService;
    private final Grid<Role> grid = new Grid<>(Role.class, false);
    private final TextField search = new TextField();
    private final ComboBox<String> typeFilter = new ComboBox<>("Type");
    private final ComboBox<String> activeFilter = new ComboBox<>("Status");
    private final Dialog sidebar = new Dialog();
    private final Dialog deactivateDialog = new Dialog();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<RoleRequest> binder = new BeanValidationBinder<>(RoleRequest.class);
    private final RoleRequest formData = new RoleRequest();
    private final TextField code = new TextField("Code");
    private final TextField name = new TextField("Name");
    private final TextArea description = new TextArea("Description");
    private final Checkbox active = new Checkbox("Active");
    private final MultiSelectComboBox<AppPermission> permissions = new MultiSelectComboBox<>("Permissions");
    private final H1 sidebarTitle = new H1();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private final Button edit = new Button("Edit");
    private final H1 deactivateTitle = new H1("Deactivate this role?");
    private final Span deactivateText = new Span();
    private List<AppPermission> availablePermissions = new ArrayList<>();
    private Role selectedRole;
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;

    public RolesView(RoleService roleService) {
        this.roleService = roleService;
        addClassName("products-view");
        availablePermissions = roleService.assignablePermissions();
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
        var title = new H1("Roles");
        var subtitle = new Span("Access profiles, permission assignments, and administrator controls.");
        subtitle.addClassName("products-subtitle");
        header.add(title, subtitle);
        return header;
    }

    private Component buildToolbar() {
        var toolbar = new HorizontalLayout();
        toolbar.addClassName("products-toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.END);

        Button newRole = new Button("New Role", event -> openCreate());
        newRole.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newRole.setVisible(roleService.canCreateRoles());

        toolbar.add(search, typeFilter, activeFilter, newRole);
        toolbar.setFlexGrow(1, search);
        return toolbar;
    }

    private void configureFilters() {
        search.setPlaceholder("Search role code or name");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.addValueChangeListener(event -> refreshGrid());

        typeFilter.setItems("All", "System", "Custom");
        typeFilter.setValue("All");
        typeFilter.setClearButtonVisible(false);
        typeFilter.addValueChangeListener(event -> refreshGrid());

        activeFilter.setItems("Active", "Inactive", "All");
        activeFilter.setValue("Active");
        activeFilter.setClearButtonVisible(false);
        activeFilter.addValueChangeListener(event -> refreshGrid());
    }

    private void configureGrid() {
        grid.addClassName("products-grid");
        grid.setSizeFull();
        grid.addColumn(roleRenderer()).setHeader("Role").setSortable(true).setAutoWidth(true).setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(this::systemBadge)).setHeader("Type").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::activeBadge)).setHeader("Active").setAutoWidth(true);
        grid.addColumn(role -> roleService.userCount(role.getId())).setHeader("User Count").setAutoWidth(true);
        grid.addColumn(role -> role.getPermissions().size()).setHeader("Permission Count").setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::actions)).setHeader("Actions").setAutoWidth(true);
        grid.addItemClickListener(event -> openView(event.getItem()));
    }

    private LitRenderer<Role> roleRenderer() {
        return LitRenderer.<Role>of("""
                <div class="product-cell">
                  <strong>${item.code}</strong>
                  <span>${item.name}</span>
                </div>
                """)
                .withProperty("code", Role::getCode)
                .withProperty("name", Role::getName);
    }

    private Component systemBadge(Role role) {
        Span badge = new Span(role.isSystemRole() ? "System" : "Custom");
        badge.addClassNames("status-badge", role.isSystemRole() ? "active-no" : "active-yes");
        return badge;
    }

    private Component activeBadge(Role role) {
        Span badge = new Span(role.isActive() ? "Active" : "Inactive");
        badge.addClassNames("status-badge", role.isActive() ? "active-yes" : "active-no");
        return badge;
    }

    private Component actions(Role role) {
        var layout = new HorizontalLayout();
        layout.addClassName("row-actions");
        Button view = new Button("View", event -> openView(role));
        layout.add(view);
        if (!role.isSystemRole() && roleService.canUpdateRoles()) {
            layout.add(new Button("Edit", event -> openEdit(role)));
        }
        if (!role.isSystemRole() && role.isActive() && roleService.canDeleteRoles()) {
            Button deactivate = new Button("Deactivate", event -> confirmDeactivate(role));
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
        form.add(code, name, description, active, permissions);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        save.addClickListener(event -> saveRole());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());
        edit.addClickListener(event -> {
            if (selectedRole != null) {
                openEdit(selectedRole);
            }
        });

        var footer = new HorizontalLayout(save, cancel, close, edit);
        footer.addClassName("sidebar-footer");

        var content = new VerticalLayout(sidebarTitle, form, footer);
        content.addClassName("sidebar-content");
        sidebar.add(content);
    }

    private void configureFields() {
        code.setRequiredIndicatorVisible(true);
        code.setValueChangeMode(ValueChangeMode.EAGER);
        name.setRequiredIndicatorVisible(true);
        name.setValueChangeMode(ValueChangeMode.EAGER);
        description.setMaxLength(500);
        description.setValueChangeMode(ValueChangeMode.EAGER);
        active.setValue(true);
        permissions.setItems(availablePermissions);
        permissions.setItemLabelGenerator(AppPermission::label);
        permissions.setRequiredIndicatorVisible(true);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(code).asRequired("Role code is required.").bind(RoleRequest::getCode, RoleRequest::setCode);
        binder.forField(name).asRequired("Role name is required.").bind(RoleRequest::getName, RoleRequest::setName);
        binder.bind(description, RoleRequest::getDescription, RoleRequest::setDescription);
        binder.bind(active, RoleRequest::isActive, RoleRequest::setActive);
        binder.forField(permissions)
                .withValidator(value -> value != null && !value.isEmpty(), "At least one permission must be selected.")
                .bind(RoleRequest::getPermissions, RoleRequest::setPermissions);
    }

    private void configureDialogs() {
        Button confirm = new Button("Deactivate", event -> deactivateSelected());
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        Button cancelDeactivate = new Button("Cancel", event -> deactivateDialog.close());
        deactivateDialog.add(new VerticalLayout(
                deactivateTitle,
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
        mode = FormMode.CREATE;
        selectedRole = null;
        sidebarTitle.setText("New Role");
        resetForm(new RoleRequest());
        setReadOnly(false);
        code.setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openEdit(Role role) {
        selectedRole = roleService.get(role.getId());
        if (selectedRole.isSystemRole()) {
            showError("System roles cannot be edited.");
            openView(selectedRole);
            return;
        }
        mode = FormMode.EDIT;
        sidebarTitle.setText("Edit Role");
        resetForm(fromRole(selectedRole));
        setReadOnly(false);
        code.setReadOnly(true);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openView(Role role) {
        selectedRole = roleService.get(role.getId());
        mode = FormMode.VIEW;
        sidebarTitle.setText("Role Details");
        resetForm(fromRole(selectedRole));
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        edit.setVisible(roleService.canUpdateRoles() && !selectedRole.isSystemRole());
        dirty = false;
        sidebar.open();
    }

    private void resetForm(RoleRequest request) {
        formData.setCode(request.getCode());
        formData.setName(request.getName());
        formData.setDescription(request.getDescription());
        formData.setActive(request.isActive());
        formData.setPermissions(request.getPermissions());
        formData.setVersion(request.getVersion());
        binder.readBean(formData);
        code.setInvalid(false);
        name.setInvalid(false);
        description.setInvalid(false);
        permissions.setInvalid(false);
    }

    private RoleRequest fromRole(Role role) {
        var request = new RoleRequest();
        request.setCode(role.getCode());
        request.setName(role.getName());
        request.setDescription(role.getDescription());
        request.setActive(role.isActive());
        request.setVersion(role.getVersion());
        request.setPermissions(role.getPermissions());
        return request;
    }

    private void saveRole() {
        if (!binder.writeBeanIfValid(formData)) {
            showError("Please fix the highlighted fields.");
            return;
        }
        try {
            if (mode == FormMode.CREATE) {
                roleService.create(formData);
                showSuccess("Role created.");
            } else if (mode == FormMode.EDIT && selectedRole != null) {
                roleService.update(selectedRole.getId(), formData);
                showSuccess("Role updated.");
            }
            dirty = false;
            sidebar.close();
            refreshGrid();
        } catch (RoleException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDeactivate(Role role) {
        selectedRole = role;
        long users = roleService.userCount(role.getId());
        if (users > 0) {
            deactivateText.setText("This role has " + users
                    + " users. Deactivating the role will not affect their existing assignments, but new users cannot be assigned to this role.");
        } else {
            deactivateText.setText("Users with this role will retain it, but new users cannot be assigned to it.");
        }
        deactivateDialog.open();
    }

    private void deactivateSelected() {
        try {
            roleService.deactivate(selectedRole.getId());
            deactivateDialog.close();
            dirty = false;
            sidebar.close();
            showSuccess("Role deactivated.");
            refreshGrid();
        } catch (RoleException | AccessDeniedException exception) {
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
        code.setReadOnly(readOnly);
        name.setReadOnly(readOnly);
        description.setReadOnly(readOnly);
        active.setReadOnly(readOnly);
        permissions.setReadOnly(readOnly);
    }

    private void refreshGrid() {
        grid.setItems(roleService.search(new RoleFilter(search.getValue(), typeFilterValue(), activeFilterValue())));
    }

    private Boolean typeFilterValue() {
        if ("System".equals(typeFilter.getValue())) {
            return true;
        }
        if ("Custom".equals(typeFilter.getValue())) {
            return false;
        }
        return null;
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
