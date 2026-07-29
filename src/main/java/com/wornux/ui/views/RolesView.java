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
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.masterdetaillayout.MasterDetailLayout;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.security.permission.AppPermission;
import com.wornux.security.permission.AppResource;
import com.wornux.user.AppUser;
import com.wornux.user.Role;
import com.wornux.user.RoleException;
import com.wornux.user.RoleFilter;
import com.wornux.user.RoleRequest;
import com.wornux.user.RoleService;
import com.wornux.user.UserException;
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
import org.jspecify.annotations.NonNull;
import org.springframework.security.access.AccessDeniedException;

@Route("roles")
@PageTitle("Roles")
@PermitAll
public class RolesView extends MasterDetailLayout {

    private enum RoleTab {
        INFORMATION,
        PERMISSIONS,
        MEMBERS
    }

    private final RoleService roleService;
    private final TextField roleSearch = new TextField();
    private final ComboBox<String> activeFilter = new ComboBox<>();
    private final Grid<Role> roleGrid = new Grid<>(Role.class, false);
    private final VerticalLayout roleHeader = new VerticalLayout();
    private final Div tabContent = new Div();
    private final Tabs tabs = new Tabs(new Tab("Information"), new Tab("Permissions"), new Tab("Members"));
    private final VerticalLayout detailContent = new VerticalLayout();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<RoleRequest> binder = new BeanValidationBinder<>(RoleRequest.class);
    private final RoleRequest formData = new RoleRequest();
    private final TextField code = new TextField("Code");
    private final TextField name = new TextField("Name");
    private final TextArea description = new TextArea("Description");
    private final IntegerField priority = new IntegerField("Priority");
    private final Checkbox active = new Checkbox("Active");
    private final MultiSelectComboBox<AppPermission> permissions = new MultiSelectComboBox<>("Permissions");
    private final H2 detailTitle = new H2();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private List<AppPermission> availablePermissions = new ArrayList<>();
    private List<Role> visibleRoles = List.of();
    private Map<Long, Long> roleMemberCounts = Map.of();
    private Long selectedRoleId;
    private Role selectedRole;
    private RoleTab selectedTab = RoleTab.INFORMATION;
    private boolean dirty;
    private boolean restoringFilters;
    private String appliedSearch = "";
    private String appliedActiveStatus = "Active";
    private Runnable deferredAction;

    public RolesView(RoleService roleService) {
        this.roleService = roleService;
        setSizeFull();
        addClassName("crud-master-detail");
        setExpandMaster(true);
        setMasterSize("48rem");
        setDetailSize("30rem");
        setOverlaySize("min(30rem, 100%)");
        availablePermissions = roleService.assignablePermissions();
        configureFilters();
        configureRoleList();
        refreshRoleList();
        configureTabs();
        configureDetail();
        configureDirtyDialog();

        var master = new Div(createHeader(), createWorkspace());
        master.setSizeFull();
        master.addClassName("role-management-view");
        setMaster(master);
        addBackdropClickListener(event -> requestClose());
        addDetailEscapePressListener(event -> requestClose());
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
        var filters = new HorizontalLayout(activeFilter);
        filters.addClassName("role-management-filters");
        filters.setWidthFull();
        filters.setFlexGrow(1, activeFilter);

        var createRole = new Button(
                "Create role", new SvgIcon("/icons/grid-create.svg"), event -> requestSwitch(this::openCreate));
        createRole.setAriaLabel("Create role");
        createRole.setTooltipText("Create role");
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

        activeFilter.setAriaLabel("Filter by role status");
        activeFilter.setItems("Active", "Inactive", "All statuses");
        activeFilter.setValue(appliedActiveStatus);
        activeFilter.setWidthFull();

        roleSearch.addValueChangeListener(event -> filterChanged());
        activeFilter.addValueChangeListener(event -> filterChanged());
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
        String searchValue = roleSearch.getValue();
        String activeStatusValue = activeFilter.getValue();
        var filter = new RoleFilter(searchValue, activeFilterValue(activeStatusValue));
        List<Role> refreshedRoles = roleService.search(filter);
        Map<Long, Long> refreshedMemberCounts =
                roleService.userCounts(refreshedRoles.stream().map(Role::getId).toList());
        Long refreshedSelection = refreshedRoles.stream()
                .filter(role -> Objects.equals(role.getId(), selectedRoleId))
                .findFirst()
                .or(() -> refreshedRoles.stream().findFirst())
                .map(Role::getId)
                .orElse(null);
        Runnable applyRefresh = () -> {
            visibleRoles = refreshedRoles;
            roleMemberCounts = refreshedMemberCounts;
            selectedRoleId = refreshedSelection;
            appliedSearch = searchValue;
            appliedActiveStatus = activeStatusValue;
            roleGrid.setItems(visibleRoles);
            roleGrid.getColumns().getFirst().setHeader("Roles · " + visibleRoles.size());
            renderSelectedRole();
        };

        if (!Objects.equals(refreshedSelection, selectedRoleId)) {
            requestMasterSwitch(applyRefresh);
            return;
        }

        applyRefresh.run();
    }

    private void selectRole(Role role) {
        if (Objects.equals(role.getId(), selectedRoleId)) {
            return;
        }

        requestMasterSwitch(() -> {
            selectedRoleId = role.getId();
            roleGrid.getDataProvider().refreshAll();
            renderSelectedRole();
        });
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
        var copy = getHeader(role);

        var actions = new HorizontalLayout();
        actions.addClassName("role-management-header-actions");

        if (roleService.canUpdateRole(role)) {
            actions.add(new Button("Edit role", event -> requestSwitch(() -> openEdit(role))));
        }

        if (role.isActive() && roleService.canDeactivateRole(role)) {
            var deactivate = new Button("Deactivate", event -> requestDestructive(() -> confirmDeactivate(role)));
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

    private static @NonNull VerticalLayout getHeader(Role role) {
        var title = new H2(role.getName());
        var meta = new Span("Priority %d · %s · %d permissions"
                .formatted(
                        role.getPriority(),
                        role.isActive() ? "Active" : "Inactive",
                        role.getPermissions().size()));
        meta.addClassName("role-management-role-meta");

        var copy = new VerticalLayout(title, meta);
        copy.setPadding(false);
        copy.setSpacing(false);
        copy.addClassName("role-management-role-header-copy");
        return copy;
    }

    private Component createInformationTab(Role role) {
        var code = readOnly(new TextField("Code"), role.getCode());
        var name = readOnly(new TextField("Name"), role.getName());
        var description = new TextArea("Description");
        description.setValue(nullToBlank(role.getDescription()));
        description.setReadOnly(true);
        description.setMinHeight("8rem");
        var priority = new IntegerField("Priority");
        priority.setValue(role.getPriority());
        priority.setReadOnly(true);
        var active = new Checkbox("Active", role.isActive());
        active.setReadOnly(true);

        var form = new FormLayout(code, name, description, priority, active);
        form.setWidthFull();
        form.setColspan(description, 2);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("36rem", 2));

        var content = createTabLayout("Information", "Review the role identity, description, priority, and availability.");
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
                "Permissions", "Permissions are grouped by resource. Use Edit role to change this assignment.");
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
        boolean canManageMembers = roleService.canAssignRole(role);
        var members = new Grid<>(AppUser.class, false);
        members.addColumn(memberIdentityRenderer()).setHeader("Member").setFlexGrow(1);
        members.addColumn(member -> member.isActive() ? "Active" : "Inactive")
                .setHeader("Status")
                .setAutoWidth(true);

        if (canManageMembers) {
            members.addColumn(new ComponentRenderer<>(member -> createRemoveMemberButton(role, member)))
                    .setHeader("Actions")
                    .setAutoWidth(true)
                    .setFlexGrow(0);
        }

        members.setItems(roleService.members(role.getId()));
        members.setEmptyStateText("No users are assigned to this role.");
        members.addClassName("role-management-members-grid");

        var content = createTabLayout(
                "Members · " + roleMemberCounts.getOrDefault(role.getId(), 0L),
                canManageMembers
                        ? "Add existing users to this role or remove current assignments."
                        : "Users assigned to this global role.");

        if (canManageMembers && role.isActive()) {
            var addMember = new Button("Add member", event -> openAddMemberDialog(role));
            addMember.addThemeVariants(ButtonVariant.PRIMARY);
            content.add(addMember);
        }

        content.add(members);
        content.addClassName("role-management-members-content");

        return content;
    }

    private Button createRemoveMemberButton(Role role, AppUser member) {
        var remove = new Button("Remove", event -> confirmRemoveMember(role, member));
        remove.setAriaLabel("Remove " + member.getUsername() + " from " + role.getName());
        remove.addThemeVariants(ButtonVariant.ERROR);

        return remove;
    }

    private void openAddMemberDialog(Role role) {
        List<AppUser> candidates = roleService.assignmentCandidates(role.getId());
        var member = new ComboBox<AppUser>("User");
        member.setItems(candidates);
        member.setItemLabelGenerator(user -> user.getUsername() + " · " + user.getEmail());
        member.setPlaceholder(candidates.isEmpty() ? "No users available" : "Search users");
        member.setWidthFull();

        var dialog = new Dialog();
        dialog.getElement().setAttribute("aria-label", "Add member to " + role.getName());
        var add = new Button("Add", event -> assignMember(role, member.getValue(), dialog));
        add.addThemeVariants(ButtonVariant.PRIMARY);
        add.setEnabled(false);
        member.addValueChangeListener(event -> add.setEnabled(event.getValue() != null));
        var cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(new VerticalLayout(
                new H1("Add member"),
                new Span("Assign an existing user to " + role.getName() + "."),
                member,
                new HorizontalLayout(add, cancel)));
        dialog.open();
    }

    private void assignMember(Role role, AppUser member, Dialog dialog) {
        if (member == null) {
            return;
        }

        try {
            roleService.assignMember(role.getId(), member.getId());
            dialog.close();
            refreshRoleList();
            showSuccess("Member added.");
        } catch (RoleException | UserException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmRemoveMember(Role role, AppUser member) {
        var dialog = new Dialog();
        dialog.getElement().setAttribute("aria-label", "Remove member from " + role.getName());
        var remove = new Button("Remove", event -> removeMember(role, member, dialog));
        remove.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        var cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(new VerticalLayout(
                new H1("Remove member?"),
                new Span("Remove " + member.getUsername() + " from " + role.getName() + "?"),
                new HorizontalLayout(remove, cancel)));
        dialog.open();
    }

    private void removeMember(Role role, AppUser member, Dialog dialog) {
        try {
            roleService.removeMember(role.getId(), member.getId());
            dialog.close();

            if (!roleService.canAssignRoles()) {
                getUI().ifPresent(ui -> ui.navigate(NoAccessView.class));
                return;
            }

            refreshRoleList();
            showSuccess("Member removed.");
        } catch (RoleException | UserException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
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

    private void configureDetail() {
        configureFields();
        bindForm();
        var form = new FormLayout(code, name, description, priority, active, permissions);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.PRIMARY);
        save.addClickListener(event -> saveRole());
        cancel.addClickListener(event -> requestClose());
        var footer = new HorizontalLayout(save, cancel);
        footer.addClassName("crud-detail-footer");

        detailTitle.setId("role-detail-title");
        detailContent.add(detailTitle, form, footer);
        detailContent.addClassName("crud-detail-content");
        detailContent.getElement().setAttribute("aria-labelledby", "role-detail-title");
        detailContent.getElement().setAttribute("role", "region");
    }

    private void configureFields() {
        code.setRequiredIndicatorVisible(true);
        code.setValueChangeMode(ValueChangeMode.EAGER);
        name.setRequiredIndicatorVisible(true);
        name.setValueChangeMode(ValueChangeMode.EAGER);
        description.setMaxLength(500);
        description.setValueChangeMode(ValueChangeMode.EAGER);
        priority.setMin(0);
        priority.setMax(100);
        priority.setStepButtonsVisible(true);
        permissions.setItems(availablePermissions);
        permissions.setItemLabelGenerator(AppPermission::label);
        permissions.setRequiredIndicatorVisible(true);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(code).asRequired("Role code is required.").bind(RoleRequest::getCode, RoleRequest::setCode);
        binder.forField(name).asRequired("Role name is required.").bind(RoleRequest::getName, RoleRequest::setName);
        binder.bind(description, RoleRequest::getDescription, RoleRequest::setDescription);
        binder.forField(priority)
                .asRequired("Priority is required.")
                .withValidator(value -> value >= 0 && value <= 100, "Priority must be between 0 and 100.")
                .bind(RoleRequest::getPriority, RoleRequest::setPriority);
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
            if (deferredAction != null) {
                Runnable action = deferredAction;
                deferredAction = null;
                action.run();
                return;
            }

            setDetail(null);
        });
        discard.addThemeVariants(ButtonVariant.ERROR);
        var stay = new Button("Cancel", event -> {
            deferredAction = null;
            dirtyDialog.close();
        });
        dirtyDialog.getElement().setAttribute("aria-label", "Unsaved changes");
        dirtyDialog.add(new VerticalLayout(title, text, new HorizontalLayout(discard, stay)));
    }

    private void openCreate() {
        selectedRole = null;
        detailTitle.setText("Create role");
        resetForm(new RoleRequest());
        code.setReadOnly(false);
        priority.setReadOnly(false);
        active.setReadOnly(false);
        dirty = false;
        setDetail(detailContent);
    }

    private void openEdit(Role role) {
        selectedRole = roleService.get(role.getId());
        detailTitle.setText("Edit role");
        resetForm(fromRole(selectedRole));
        code.setReadOnly(true);
        priority.setReadOnly(selectedRole.getPriority() == 100);
        active.setReadOnly(!roleService.canChangeActiveState(selectedRole));
        dirty = false;
        setDetail(detailContent);
    }

    private void resetForm(RoleRequest request) {
        formData.setCode(request.getCode());
        formData.setName(request.getName());
        formData.setDescription(request.getDescription());
        formData.setPriority(request.getPriority());
        formData.setActive(request.isActive());
        formData.setPermissions(request.getPermissions());
        formData.setVersion(request.getVersion());
        binder.readBean(formData);
        code.setInvalid(false);
        name.setInvalid(false);
        description.setInvalid(false);
        priority.setInvalid(false);
        permissions.setInvalid(false);
    }

    private RoleRequest fromRole(Role role) {
        var request = new RoleRequest();
        request.setCode(role.getCode());
        request.setName(role.getName());
        request.setDescription(role.getDescription());
        request.setPriority(role.getPriority());
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
            setDetail(null);
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
            selectedRole = null;
            if (getDetail() != null) {
                setDetail(null);
            }
            refreshRoleList();
            showSuccess("Role deactivated.");
        } catch (RoleException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void requestDestructive(Runnable action) {
        boolean discardDetail = dirty;
        requestSwitch(() -> {
            if (discardDetail) {
                selectedRole = null;
                setDetail(null);
            }

            action.run();
        });
    }

    private void requestMasterSwitch(Runnable action) {
        requestSwitch(() -> {
            if (getDetail() != null) {
                selectedRole = null;
                setDetail(null);
            }

            action.run();
        });
    }

    private void requestSwitch(Runnable action) {
        if (dirty) {
            deferredAction = action;
            dirtyDialog.open();
            return;
        }

        deferredAction = null;
        action.run();
    }

    private void requestClose() {
        deferredAction = null;
        if (dirty) {
            dirtyDialog.open();
            return;
        }

        setDetail(null);
    }

    private void filterChanged() {
        if (restoringFilters) {
            return;
        }

        String requestedSearch = roleSearch.getValue();
        String requestedActiveStatus = activeFilter.getValue();
        boolean changed = !Objects.equals(requestedSearch, appliedSearch)
                || !Objects.equals(requestedActiveStatus, appliedActiveStatus);
        if (!changed) {
            return;
        }

        if (dirty) {
            setFilters(appliedSearch, appliedActiveStatus);
            requestMasterSwitch(() -> applyFilters(requestedSearch, requestedActiveStatus));
            return;
        }

        refreshRoleList();
    }

    private void applyFilters(String searchValue, String activeStatusValue) {
        setFilters(searchValue, activeStatusValue);
        refreshRoleList();
    }

    private void setFilters(String searchValue, String activeStatusValue) {
        restoringFilters = true;
        roleSearch.setValue(searchValue);
        activeFilter.setValue(activeStatusValue);
        restoringFilters = false;
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
