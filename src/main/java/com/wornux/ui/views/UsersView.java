package com.wornux.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ModalityMode;
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
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.LitRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.wornux.user.AppUser;
import com.wornux.user.Role;
import com.wornux.user.UserException;
import com.wornux.user.UserFilter;
import com.wornux.user.UserRequest;
import com.wornux.user.UserService;
import jakarta.annotation.security.PermitAll;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;

@Route("users")
@PageTitle("Users")
@PermitAll
public class UsersView extends Main {

    private enum FormMode {
        CREATE,
        EDIT,
        VIEW
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final UserService userService;
    private final Grid<AppUser> grid = new Grid<>(AppUser.class, false);
    private final TextField search = new TextField();
    private final ComboBox<String> activeFilter = new ComboBox<>("Status");
    private final Dialog sidebar = new Dialog();
    private final Dialog deactivateDialog = new Dialog();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<UserRequest> binder = new BeanValidationBinder<>(UserRequest.class);
    private final UserRequest formData = new UserRequest();
    private final TextField username = new TextField("Username");
    private final EmailField email = new EmailField("Email");
    private final Checkbox active = new Checkbox("Active");
    private final TextField createdAt = new TextField("Created at");
    private final MultiSelectComboBox<Role> roles = new MultiSelectComboBox<>("Roles");
    private final H1 sidebarTitle = new H1();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private final Button edit = new Button("Edit");
    private List<Role> availableRoles = new ArrayList<>();
    private AppUser selectedUser;
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;

    public UsersView(UserService userService) {
        this.userService = userService;
        addClassName("products-view");
        availableRoles = userService.activeRoles();
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
        var title = new H1("Users");
        var subtitle = new Span("Account status, role assignments, and administrator controls.");
        subtitle.addClassName("products-subtitle");
        header.add(title, subtitle);

        return header;
    }

    private Component buildToolbar() {
        var toolbar = new HorizontalLayout();
        toolbar.addClassName("products-toolbar");
        toolbar.setWidthFull();
        toolbar.setAlignItems(HorizontalLayout.Alignment.END);

        Button newUser = new Button("New User", event -> openCreate());
        newUser.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newUser.setVisible(userService.canCreateUsers());

        toolbar.add(search, activeFilter, newUser);
        toolbar.setFlexGrow(1, search);

        return toolbar;
    }

    private void configureFilters() {
        search.setPlaceholder("Search username or email");
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
        grid.addColumn(userRenderer()).setHeader("User").setAutoWidth(true).setFlexGrow(2);
        grid.addColumn(user -> formatInstant(user.getCreatedAt()))
                .setHeader("Created At")
                .setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::activeBadge))
                .setHeader("Active")
                .setAutoWidth(true);
        grid.addColumn(user -> roleSummary(user.getRoles())).setHeader("Roles").setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(this::actions))
                .setHeader("Actions")
                .setAutoWidth(true);
        grid.addItemClickListener(event -> openView(event.getItem()));
    }

    private LitRenderer<AppUser> userRenderer() {
        return LitRenderer.<AppUser>of("""
                <div class="product-cell">
                  <strong>${item.username}</strong>
                  <span>${item.email}</span>
                </div>
                """)
                .withProperty("username", AppUser::getUsername)
                .withProperty("email", AppUser::getEmail);
    }

    private Component activeBadge(AppUser user) {
        Span badge = new Span(user.isActive() ? "Active" : "Inactive");
        badge.addClassNames("status-badge", user.isActive() ? "active-yes" : "active-no");

        return badge;
    }

    private Component actions(AppUser user) {
        var layout = new HorizontalLayout();
        layout.addClassName("row-actions");
        Button view = new Button("View", event -> openView(user));
        layout.add(view);

        if (userService.canUpdateUsers()) {
            layout.add(new Button("Edit", event -> openEdit(user)));
        }

        if (userService.canDeleteUsers() && user.isActive()) {
            Button deactivate = new Button("Deactivate", event -> confirmDeactivate(user));
            deactivate.addThemeVariants(ButtonVariant.LUMO_ERROR);
            layout.add(deactivate);
        }

        return layout;
    }

    private void configureSidebar() {
        sidebar.addClassName("product-sidebar");
        sidebar.setModality(ModalityMode.MODELESS);
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
        form.add(username, email, active, roles, createdAt);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        save.addClickListener(event -> saveUser());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());
        edit.addClickListener(event -> {
            if (selectedUser != null) {
                openEdit(selectedUser);
            }
        });

        var footer = new HorizontalLayout(save, cancel, close, edit);
        footer.addClassName("sidebar-footer");

        var content = new VerticalLayout(sidebarTitle, form, footer);
        content.addClassName("sidebar-content");
        sidebar.add(content);
    }

    private void configureFields() {
        username.setRequiredIndicatorVisible(true);
        username.setValueChangeMode(ValueChangeMode.EAGER);
        email.setRequiredIndicatorVisible(true);
        email.setValueChangeMode(ValueChangeMode.EAGER);
        active.setValue(true);
        roles.setItems(availableRoles);
        roles.setItemLabelGenerator(role -> role.getName() + " (" + role.getCode() + ")");
        roles.setRequiredIndicatorVisible(true);
        createdAt.setReadOnly(true);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(username)
                .asRequired("Username is required.")
                .bind(UserRequest::getUsername, UserRequest::setUsername);
        binder.forField(email).asRequired("Email is required.").bind(UserRequest::getEmail, UserRequest::setEmail);
        binder.bind(active, UserRequest::isActive, UserRequest::setActive);
        binder.forField(roles)
                .withValidator(value -> value != null && !value.isEmpty(), "At least one role must be selected.")
                .bind(
                        this::rolesFromRequest,
                        (request, value) -> request.setRoleIds(
                                value.stream().map(Role::getId).collect(Collectors.toCollection(LinkedHashSet::new))));
    }

    private void configureDialogs() {
        var deactivateTitle = new H1("Deactivate this user?");
        var deactivateText = new Span("They will not be able to log in.");
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
        selectedUser = null;
        sidebarTitle.setText("New User");
        resetForm(new UserRequest(), null);
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openEdit(AppUser user) {
        selectedUser = userService.get(user.getId());
        mode = FormMode.EDIT;
        sidebarTitle.setText("Edit User");
        resetForm(fromUser(selectedUser), selectedUser.getCreatedAt());
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        sidebar.open();
    }

    private void openView(AppUser user) {
        selectedUser = userService.get(user.getId());
        mode = FormMode.VIEW;
        sidebarTitle.setText("User Details");
        resetForm(fromUser(selectedUser), selectedUser.getCreatedAt());
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        edit.setVisible(userService.canUpdateUsers());
        dirty = false;
        sidebar.open();
    }

    private void resetForm(UserRequest request, Instant createdAtValue) {
        formData.setUsername(request.getUsername());
        formData.setEmail(request.getEmail());
        formData.setActive(request.isActive());
        formData.setRoleIds(request.getRoleIds());
        formData.setVersion(request.getVersion());
        createdAt.setValue(formatInstant(createdAtValue));
        binder.readBean(formData);
        clearValidationErrors();
    }

    private UserRequest fromUser(AppUser user) {
        var request = new UserRequest();
        request.setUsername(user.getUsername());
        request.setEmail(user.getEmail());
        request.setActive(user.isActive());
        request.setRoleIds(
                user.getRoles().stream().map(Role::getId).collect(Collectors.toCollection(LinkedHashSet::new)));
        request.setVersion(user.getVersion());

        return request;
    }

    private void saveUser() {
        if (!binder.writeBeanIfValid(formData)) {
            showError("Please fix the highlighted fields.");
            return;
        }

        try {
            if (mode == FormMode.CREATE) {
                userService.create(formData);
                showSuccess("User created.");
            } else if (mode == FormMode.EDIT && selectedUser != null) {
                userService.update(selectedUser.getId(), formData);
                showSuccess("User updated.");
            }

            dirty = false;
            sidebar.close();
            refreshGrid();
        } catch (UserException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDeactivate(AppUser user) {
        selectedUser = user;
        deactivateDialog.open();
    }

    private void deactivateSelected() {
        try {
            userService.deactivate(selectedUser.getId());
            deactivateDialog.close();
            dirty = false;
            sidebar.close();
            showSuccess("User deactivated.");
            refreshGrid();
        } catch (UserException | AccessDeniedException exception) {
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
        username.setReadOnly(readOnly);
        email.setReadOnly(readOnly);
        active.setReadOnly(readOnly);
        roles.setReadOnly(readOnly);
    }

    private Set<Role> rolesFromRequest(UserRequest request) {
        return availableRoles.stream()
                .filter(role -> request.getRoleIds().contains(role.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void refreshGrid() {
        grid.setItems(userService.search(new UserFilter(search.getValue(), activeFilterValue())));
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

    private String roleSummary(Set<Role> roles) {
        return roles.stream().map(Role::getName).sorted().collect(Collectors.joining(", "));
    }

    private String formatInstant(Instant value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private void clearValidationErrors() {
        username.setInvalid(false);
        email.setInvalid(false);
        roles.setInvalid(false);
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
