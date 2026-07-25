package com.wornux.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.wornux.security.permission.AppPermission;
import com.wornux.security.permission.AppResource;
import com.wornux.user.AppUser;
import com.wornux.user.Role;
import com.wornux.user.RoleException;
import com.wornux.user.RoleFilter;
import com.wornux.user.RoleRequest;
import com.wornux.user.RoleService;
import jakarta.annotation.security.PermitAll;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;

@Route("roles")
@PageTitle("Roles")
@PermitAll
public class RolesView extends Div {

    private enum RoleTab {
        INFORMATION,
        PERMISSIONS,
        MEMBERS
    }

    private final RoleService roleService;
    private final TextField roleSearch = new TextField();
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final ComboBox<String> activeFilter = new ComboBox<>();
    private final ValueSignal<String> searchSignal = new ValueSignal<>("");
    private final ValueSignal<String> typeSignal = new ValueSignal<>("All types");
    private final ValueSignal<String> activeStatusSignal = new ValueSignal<>("Active");
    private final Signal<RoleFilter> filterSignal = Signal.computed(() -> new RoleFilter(
            searchSignal.get(), typeFilterValue(typeSignal.get()), activeFilterValue(activeStatusSignal.get())));
    private final Grid<Role> roleGrid = new Grid<>(Role.class, false);
    private final VerticalLayout roleHeader = new VerticalLayout();
    private final Div tabContent = new Div();
    private final Tabs tabs = new Tabs(new Tab("Information"), new Tab("Permissions"), new Tab("Members"));
    private final Dialog sidebar = new Dialog();
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
    private List<AppPermission> availablePermissions = new ArrayList<>();
    private List<Role> visibleRoles = List.of();
    private Map<Long, Long> roleMemberCounts = Map.of();
    private Long selectedRoleId;
    private Role selectedRole;
    private RoleTab selectedTab = RoleTab.INFORMATION;
    private boolean dirty;

    public RolesView(RoleService roleService) {
        this.roleService = roleService;
        setSizeFull();
        addClassName("role-management-view");
        availablePermissions = roleService.assignablePermissions();
        configureFilters();
        configureRoleList();
        Signal.effect(roleGrid, () -> {
            filterSignal.get();
            refreshRoleList();
        });
        configureTabs();
        configureSidebar();
        configureDirtyDialog();
        add(createHeader(), createWorkspace());
    }

    private Component createHeader() {
        var title = new H1("Roles and permissions");
        var description = new Paragraph("Manage global roles, their permissions, and assigned members from one place.");
        description.addClassName("role-management-description");
        var header = new Header(title, description);
        header.addClassName("role-management-header");

        return header;
    }

    private Component createWorkspace() {
        var workspace = new Div(createRolePanel(), createEditorPanel());
        workspace.addClassName("role-management-split");

        return workspace;
    }

    private Component createRolePanel() {
        var filters = new HorizontalLayout(typeFilter, activeFilter);
        filters.addClassName("role-management-filters");
        filters.setWidthFull();
        filters.setFlexGrow(1, typeFilter, activeFilter);

        var createRole = new Button("Create role", event -> openCreate());
        createRole.addThemeVariants(ButtonVariant.PRIMARY);
        createRole.setVisible(roleService.canCreateRoles());

        var controls = new VerticalLayout(roleSearch, filters, createRole);
        controls.setPadding(false);
        controls.setSpacing(false);
        controls.addClassName("role-management-controls");

        var panel = new VerticalLayout(new H2("Roles"), controls, roleGrid);
        panel.setSizeFull();
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.addClassName("role-management-role-panel");

        return panel;
    }

    private Component createEditorPanel() {
        roleHeader.setPadding(false);
        roleHeader.setSpacing(false);
        tabContent.addClassName("role-management-tab-content");

        var panel = new VerticalLayout(roleHeader, tabs, tabContent);
        panel.setSizeFull();
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.addClassName("role-management-editor-panel");

        return panel;
    }

    private void configureFilters() {
        roleSearch.setPlaceholder("Search roles");
        roleSearch.setAriaLabel("Search roles");
        roleSearch.setClearButtonVisible(true);
        roleSearch.setValueChangeMode(ValueChangeMode.LAZY);
        roleSearch.setWidthFull();
        roleSearch.bindValue(searchSignal, searchSignal::set);

        typeFilter.setAriaLabel("Filter by role type");
        typeFilter.setItems("All types", "System", "Custom");
        typeFilter.setWidthFull();
        typeFilter.bindValue(typeSignal, typeSignal::set);

        activeFilter.setAriaLabel("Filter by role status");
        activeFilter.setItems("Active", "Inactive", "All statuses");
        activeFilter.setWidthFull();
        activeFilter.bindValue(activeStatusSignal, activeStatusSignal::set);
    }

    private void configureRoleList() {
        roleGrid.setSelectionMode(Grid.SelectionMode.NONE);
        roleGrid.setSizeFull();
        roleGrid.setEmptyStateText("No roles match the current filters.");
        roleGrid.addClassName("role-management-role-list");
        roleGrid.addColumn(roleIdentityRenderer()).setHeader("Roles").setFlexGrow(1);
        roleGrid.addColumn(roleMemberCountRenderer())
                .setHeader("Members")
                .setAutoWidth(true)
                .setFlexGrow(0);
    }

    private LitRenderer<Role> roleIdentityRenderer() {
        return LitRenderer.<Role>of("""
                <button class="role-management-role-entry ${item.selected}" @click="${selectRole}" aria-label="Select ${item.name}" aria-pressed="${item.pressed}">
                    <span class="role-management-role-entry-icon" aria-hidden="true"></span>
                    <span class="role-management-role-entry-copy">
                        <span class="role-management-role-entry-name">${item.name}</span>
                    </span>
                </button>
                """)
                .withProperty("name", Role::getName)
                .withProperty("selected", role -> Objects.equals(role.getId(), selectedRoleId) ? "is-selected" : "")
                .withProperty("pressed", role -> Objects.equals(role.getId(), selectedRoleId))
                .withFunction("selectRole", this::selectRole);
    }

    private LitRenderer<Role> roleMemberCountRenderer() {
        return LitRenderer.<Role>of("""
                <span class="role-management-role-count" aria-label="${item.count} members">
                    <span>${item.count}</span>
                    <vaadin-icon src="/icons/IconPeople.svg" aria-hidden="true"></vaadin-icon>
                </span>
                """).withProperty("count", role -> roleMemberCounts.getOrDefault(role.getId(), 0L));
    }

    private void configureTabs() {
        tabs.setWidthFull();
        tabs.addClassName("role-management-tabs");
        tabs.addSelectedChangeListener(event -> {
            selectedTab = RoleTab.values()[tabs.getSelectedIndex()];
            renderSelectedRole();
        });
    }

    private void refreshRoleList() {
        visibleRoles = roleService.search(filterSignal.peek());
        roleMemberCounts =
                roleService.userCounts(visibleRoles.stream().map(Role::getId).toList());
        selectedRoleId = visibleRoles.stream()
                .filter(role -> Objects.equals(role.getId(), selectedRoleId))
                .findFirst()
                .or(() -> visibleRoles.stream().findFirst())
                .map(Role::getId)
                .orElse(null);
        roleGrid.setItems(visibleRoles);
        roleGrid.getColumns().getFirst().setHeader("Roles · " + visibleRoles.size());
        renderSelectedRole();
    }

    private void selectRole(Role role) {
        selectedRoleId = role.getId();
        roleGrid.getDataProvider().refreshAll();
        renderSelectedRole();
    }

    private void renderSelectedRole() {
        roleHeader.removeAll();
        tabContent.removeAll();
        Role role = currentRole();

        if (role == null) {
            tabs.setVisible(false);
            roleHeader.add(emptyState("Select or create a role to get started."));
            return;
        }

        tabs.setVisible(true);
        roleHeader.add(createSelectedRoleHeader(role));
        tabContent.add(
                switch (selectedTab) {
                    case INFORMATION -> createInformationTab(role);
                    case PERMISSIONS -> createPermissionsTab(role);
                    case MEMBERS -> createMembersTab(role);
                });
    }

    private Role currentRole() {
        return visibleRoles.stream()
                .filter(role -> Objects.equals(role.getId(), selectedRoleId))
                .findFirst()
                .orElse(null);
    }

    private Component createSelectedRoleHeader(Role role) {
        var title = new H2(role.getName());
        var meta = new Span("%s · %s · %s · %d permissions"
                .formatted(
                        role.getCode(),
                        role.isSystemRole() ? "System role" : "Custom role",
                        role.isActive() ? "Active" : "Inactive",
                        role.getPermissions().size()));
        meta.addClassName("role-management-role-meta");

        var copy = new VerticalLayout(title, meta);
        copy.setPadding(false);
        copy.setSpacing(false);
        copy.addClassName("role-management-role-header-copy");

        var actions = new HorizontalLayout();
        actions.addClassName("role-management-header-actions");

        if (!role.isSystemRole() && roleService.canUpdateRoles()) {
            actions.add(new Button("Edit role", event -> openEdit(role)));
        }

        if (!role.isSystemRole() && role.isActive() && roleService.canDeleteRoles()) {
            var deactivate = new Button("Deactivate", event -> confirmDeactivate(role));
            deactivate.addThemeVariants(ButtonVariant.ERROR);
            actions.add(deactivate);
        }

        var header = new HorizontalLayout(copy, actions);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        header.addClassName("role-management-role-header");

        return header;
    }

    private Component createInformationTab(Role role) {
        var code = readOnly(new TextField("Code"), role.getCode());
        var name = readOnly(new TextField("Name"), role.getName());
        var description = new TextArea("Description");
        description.setValue(nullToBlank(role.getDescription()));
        description.setReadOnly(true);
        description.setMinHeight("8rem");
        var active = new Checkbox("Active", role.isActive());
        active.setReadOnly(true);
        var system = new Checkbox("System role", role.isSystemRole());
        system.setReadOnly(true);

        var form = new FormLayout(code, name, description, active, system);
        form.setWidthFull();
        form.setColspan(description, 2);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("36rem", 2));

        var content = createTabLayout("Information", "Review the role identity, description, type, and availability.");
        content.add(form);

        return content;
    }

    private TextField readOnly(TextField field, String value) {
        field.setValue(value);
        field.setReadOnly(true);

        return field;
    }

    private Component createPermissionsTab(Role role) {
        var search = new TextField();
        var groups = new VerticalLayout();
        search.setPlaceholder("Search permissions");
        search.setAriaLabel("Search permissions");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setWidthFull();
        groups.setPadding(false);
        groups.setSpacing(false);
        groups.addClassName("role-management-permission-rows");
        search.addValueChangeListener(event -> renderPermissionGroups(role, groups, search.getValue()));
        renderPermissionGroups(role, groups, "");

        var content = createTabLayout(
                "Permissions", "Permissions are grouped by resource. Edit custom roles with the action above.");
        content.add(search, groups);

        return content;
    }

    private void renderPermissionGroups(Role role, VerticalLayout groups, String query) {
        groups.removeAll();
        permissionsByResource().forEach((resource, resourcePermissions) -> {
            var visible = resourcePermissions.stream()
                    .filter(permissionMatches(query))
                    .toList();

            if (!visible.isEmpty()) {
                groups.add(createPermissionGroup(role, resource, visible));
            }
        });

        if (groups.getComponentCount() == 0) {
            groups.add(emptyState("No permissions match the search."));
        }
    }

    private Predicate<AppPermission> permissionMatches(String query) {
        String normalized = nullToBlank(query).trim().toLowerCase(Locale.ROOT);

        return permission -> normalized.isBlank()
                || permission.code().contains(normalized)
                || permission.label().toLowerCase(Locale.ROOT).contains(normalized);
    }

    private Component createPermissionGroup(Role role, AppResource resource, List<AppPermission> resourcePermissions) {
        var rows = new VerticalLayout();
        rows.setPadding(false);
        rows.setSpacing(false);
        rows.addClassName("role-management-permission-rows");
        resourcePermissions.forEach(permission -> rows.add(createPermissionRow(role, permission)));

        var group = new VerticalLayout(new H3(resource.label()), rows);
        group.setWidthFull();
        group.setPadding(false);
        group.setSpacing(false);
        group.addClassName("role-management-permission-group");

        return group;
    }

    private Component createPermissionRow(Role role, AppPermission permission) {
        var checkbox = new Checkbox(permission.action().label());
        checkbox.setValue(role.getPermissions().contains(permission));
        checkbox.setReadOnly(true);
        var code = new Span(permission.code());
        code.addClassName("role-management-permission-code");
        var row = new HorizontalLayout(checkbox, code);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        row.addClassName("role-management-permission-row");

        return row;
    }

    private Map<AppResource, List<AppPermission>> permissionsByResource() {
        return Arrays.stream(AppPermission.values())
                .collect(Collectors.groupingBy(AppPermission::resource, LinkedHashMap::new, Collectors.toList()));
    }

    private Component createMembersTab(Role role) {
        var members = new Grid<>(AppUser.class, false);
        members.addColumn(memberIdentityRenderer()).setHeader("Member").setFlexGrow(1);
        members.addColumn(member -> member.isActive() ? "Active" : "Inactive")
                .setHeader("Status")
                .setAutoWidth(true);
        members.setItems(roleService.members(role.getId()));
        members.setEmptyStateText("No users are assigned to this role.");
        members.addClassName("role-management-members-grid");

        var content = createTabLayout(
                "Members · " + roleMemberCounts.getOrDefault(role.getId(), 0L),
                "Users assigned to this global role. Manage assignments from the Users view.");
        content.add(members);
        content.addClassName("role-management-members-content");

        return content;
    }

    private LitRenderer<AppUser> memberIdentityRenderer() {
        return LitRenderer.<AppUser>of("""
                <div class="role-management-member-cell">
                    <strong>${item.username}</strong>
                    <span>${item.email}</span>
                </div>
                """)
                .withProperty("username", AppUser::getUsername)
                .withProperty("email", AppUser::getEmail);
    }

    private VerticalLayout createTabLayout(String title, String helper) {
        var heading = new H3(title);
        var description = new Paragraph(helper);
        description.addClassName("role-management-tab-description");
        var layout = new VerticalLayout(heading, description);
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setWidthFull();
        layout.addClassName("role-management-tab-layout");

        return layout;
    }

    private Component emptyState(String message) {
        var state = new Paragraph(message);
        state.addClassName("role-management-empty-state");

        return state;
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
        var form = new FormLayout(code, name, description, active, permissions);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(event -> saveRole());
        cancel.addClickListener(event -> requestClose());
        var footer = new HorizontalLayout(save, cancel);
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

    private void configureDirtyDialog() {
        var title = new H1("Unsaved changes");
        var text = new Span("You have unsaved changes. Discard them?");
        var discard = new Button("Discard", event -> {
            dirty = false;
            dirtyDialog.close();
            sidebar.close();
        });
        discard.addThemeVariants(ButtonVariant.ERROR);
        var stay = new Button("Cancel", event -> dirtyDialog.close());
        dirtyDialog.getElement().setAttribute("aria-label", "Unsaved changes");
        dirtyDialog.add(new VerticalLayout(title, text, new HorizontalLayout(discard, stay)));
    }

    private void openCreate() {
        selectedRole = null;
        sidebarTitle.setText("Create role");
        sidebar.getElement().setAttribute("aria-label", "Create role");
        resetForm(new RoleRequest());
        code.setReadOnly(false);
        dirty = false;
        sidebar.open();
    }

    private void openEdit(Role role) {
        selectedRole = roleService.get(role.getId());

        if (selectedRole.isSystemRole()) {
            showError("System roles cannot be edited.");
            return;
        }

        sidebarTitle.setText("Edit role");
        sidebar.getElement().setAttribute("aria-label", "Edit role");
        resetForm(fromRole(selectedRole));
        code.setReadOnly(true);
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
            Role saved = selectedRole == null
                    ? roleService.create(formData)
                    : roleService.update(selectedRole.getId(), formData);
            selectedRoleId = saved.getId();
            dirty = false;
            sidebar.close();
            refreshRoleList();
            showSuccess(selectedRole == null ? "Role created." : "Role updated.");
        } catch (RoleException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDeactivate(Role role) {
        long users = roleMemberCounts.getOrDefault(role.getId(), 0L);
        var dialog = new Dialog();
        dialog.getElement().setAttribute("aria-label", "Deactivate this role");
        var title = new H1("Deactivate this role?");
        var text = new Span(
                users > 0
                        ? "This role has %d users. They will retain the assignment, but it will stop granting permissions."
                                .formatted(users)
                        : "The role will become unavailable for new assignments.");
        var confirm = new Button("Deactivate", event -> deactivate(role, dialog));
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        var cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(new VerticalLayout(title, text, new HorizontalLayout(confirm, cancel)));
        dialog.open();
    }

    private void deactivate(Role role, Dialog dialog) {
        try {
            roleService.deactivate(role.getId());
            dialog.close();
            refreshRoleList();
            showSuccess("Role deactivated.");
        } catch (RoleException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void requestClose() {
        if (dirty) {
            dirtyDialog.open();
            return;
        }

        sidebar.close();
    }

    private Boolean typeFilterValue(String value) {
        if ("System".equals(value)) {
            return true;
        }

        if ("Custom".equals(value)) {
            return false;
        }

        return null;
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

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private void showSuccess(String message) {
        var notification = Notification.show(message, 3000, Notification.Position.TOP_END);
        notification.addThemeVariants(NotificationVariant.SUCCESS);
    }

    private void showError(String message) {
        var notification = Notification.show(message, 5000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.ERROR);
    }
}
