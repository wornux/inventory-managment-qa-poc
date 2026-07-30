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
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
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
public class UsersView extends MasterDetailLayout {

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
    private final ValueSignal<String> searchSignal = new ValueSignal<>("");
    private final ValueSignal<String> activeStatusSignal = new ValueSignal<>("Active");
    private final Signal<UserFilter> filterSignal =
            Signal.computed(() -> new UserFilter(searchSignal.get(), activeFilterValue(activeStatusSignal.get())));
    private final VerticalLayout detailContent = new VerticalLayout();
    private final Dialog deactivateDialog = new Dialog();
    private final Dialog dirtyDialog = new Dialog();
    private final BeanValidationBinder<UserRequest> binder = new BeanValidationBinder<>(UserRequest.class);
    private final UserRequest formData = new UserRequest();
    private final TextField username = new TextField("Username");
    private final EmailField email = new EmailField("Email");
    private final PasswordField password = new PasswordField("Password");
    private final Checkbox active = new Checkbox("Active");
    private final TextField createdAt = new TextField("Created at");
    private final MultiSelectComboBox<Role> roles = new MultiSelectComboBox<>("Roles");
    private final H2 detailTitle = new H2();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final Button close = new Button("Close");
    private final Button edit = new Button("Edit");
    private List<Role> availableRoles = new ArrayList<>();
    private AppUser selectedUser;
    private AppUser deactivateTarget;
    private GridLazyDataView<AppUser> gridDataView;
    private FormMode mode = FormMode.VIEW;
    private boolean dirty;
    private Runnable deferredAction;

    public UsersView(UserService userService) {
        this.userService = userService;
        setSizeFull();
        addClassName("crud-master-detail");
        setExpandMaster(true);
        setMasterSize("36rem");
        setDetailSize("30rem");
        setOverlaySize("min(30rem, 100%)");
        availableRoles = userService.activeRoles();
        configureFilters();
        configureGrid();
        gridDataView = PageableGridBinding.bind(grid, filterSignal, userService::search);
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

        Button newUser =
                new Button("New User", new SvgIcon("/icons/grid-create.svg"), event -> requestSwitch(this::openCreate));
        newUser.setAriaLabel("New User");
        newUser.setTooltipText("New User");
        newUser.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        newUser.setVisible(userService.canCreateUsers());

        toolbar.add(search, activeFilter, newUser);
        toolbar.setFlexGrow(1, search);

        return toolbar;
    }

    private void configureFilters() {
        search.setPlaceholder("Search username or email");
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
        var userColumn = grid.addColumn(userRenderer())
                .setHeader("User")
                .setSortProperty("username", "id")
                .setAutoWidth(true)
                .setFlexGrow(2);
        grid.addColumn(user -> formatInstant(user.getCreatedAt()))
                .setHeader("Created At")
                .setSortProperty("createdDate", "id")
                .setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(this::activeBadge))
                .setHeader("Active")
                .setAutoWidth(true);
        grid.addColumn(user -> roleSummary(user.getRoles())).setHeader("Roles").setFlexGrow(2);
        grid.addColumn(new ComponentRenderer<>(this::actions))
                .setHeader("Actions")
                .setAutoWidth(true);
        grid.addItemClickListener(event -> requestSwitch(() -> openView(event.getItem())));
        grid.sort(List.of(new GridSortOrder<>(userColumn, SortDirection.ASCENDING)));
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
        Button view = new Button("View", event -> requestSwitch(() -> openView(user)));
        layout.add(view);

        if (userService.canUpdateUsers()) {
            layout.add(new Button("Edit", event -> requestSwitch(() -> openEdit(user))));
        }

        if (userService.canDeleteUsers() && user.isActive()) {
            Button deactivate = new Button("Deactivate", event -> requestDestructive(() -> confirmDeactivate(user)));
            deactivate.addThemeVariants(ButtonVariant.LUMO_ERROR);
            layout.add(deactivate);
        }

        return layout;
    }

    private void configureDetail() {
        configureFields();
        bindForm();

        var form = new FormLayout();
        form.add(username, email, password, active, roles, createdAt);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);
        save.addClickListener(event -> saveUser());
        cancel.addClickListener(event -> requestClose());
        close.addClickListener(event -> requestClose());
        edit.addClickListener(event -> {
            if (selectedUser != null) {
                requestSwitch(() -> openEdit(selectedUser));
            }
        });

        var footer = new HorizontalLayout(save, cancel, close, edit);
        footer.addClassName("crud-detail-footer");

        detailTitle.setId("user-detail-title");
        detailContent.add(detailTitle, form, footer);
        detailContent.addClassName("crud-detail-content");
        detailContent.getElement().setAttribute("aria-labelledby", "user-detail-title");
        detailContent.getElement().setAttribute("role", "region");
    }

    private void configureFields() {
        username.setRequiredIndicatorVisible(true);
        username.setValueChangeMode(ValueChangeMode.EAGER);
        email.setRequiredIndicatorVisible(true);
        email.setValueChangeMode(ValueChangeMode.EAGER);
        password.setMinLength(8);
        password.setHelperText("At least 8 characters.");
        password.setValueChangeMode(ValueChangeMode.EAGER);
        password.getElement().setAttribute("autocomplete", "new-password");
        password.setVisible(false);
        active.setValue(true);
        roles.setItems(availableRoles);
        roles.setItemLabelGenerator(Role::getName);
        roles.setRequiredIndicatorVisible(true);
        createdAt.setReadOnly(true);
        binder.addValueChangeListener(event -> dirty = true);
    }

    private void bindForm() {
        binder.forField(username)
                .asRequired("Username is required.")
                .bind(UserRequest::getUsername, UserRequest::setUsername);
        binder.forField(email).asRequired("Email is required.").bind(UserRequest::getEmail, UserRequest::setEmail);
        binder.forField(password)
                .withValidator(
                        value -> mode != FormMode.CREATE
                                || (value != null && !value.isBlank() && value.length() >= 8),
                        "Password must be at least 8 characters.")
                .bind(UserRequest::getPassword, UserRequest::setPassword);
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
        selectedUser = null;
        detailTitle.setText("New User");
        resetForm(new UserRequest(), null);
        password.setVisible(true);
        password.setRequiredIndicatorVisible(true);
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        setDetail(detailContent);
    }

    private void openEdit(AppUser user) {
        selectedUser = userService.get(user.getId());
        mode = FormMode.EDIT;
        detailTitle.setText("Edit User");
        resetForm(fromUser(selectedUser), selectedUser.getCreatedAt());
        password.setVisible(false);
        password.setRequiredIndicatorVisible(false);
        setReadOnly(false);
        save.setVisible(true);
        cancel.setVisible(true);
        close.setVisible(false);
        edit.setVisible(false);
        dirty = false;
        setDetail(detailContent);
    }

    private void openView(AppUser user) {
        selectedUser = userService.get(user.getId());
        mode = FormMode.VIEW;
        detailTitle.setText("User Details");
        resetForm(fromUser(selectedUser), selectedUser.getCreatedAt());
        password.setVisible(false);
        password.setRequiredIndicatorVisible(false);
        setReadOnly(true);
        save.setVisible(false);
        cancel.setVisible(false);
        close.setVisible(true);
        edit.setVisible(userService.canUpdateUsers());
        dirty = false;
        setDetail(detailContent);
    }

    private void resetForm(UserRequest request, Instant createdAtValue) {
        formData.setUsername(request.getUsername());
        formData.setEmail(request.getEmail());
        formData.setPassword(null);
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
            setDetail(null);
            refreshGrid();
        } catch (UserException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void confirmDeactivate(AppUser user) {
        deactivateTarget = user;
        deactivateDialog.open();
    }

    private void deactivateSelected() {
        try {
            userService.deactivate(deactivateTarget.getId());
            deactivateDialog.close();
            deactivateTarget = null;
            dirty = false;
            setDetail(null);
            showSuccess("User deactivated.");
            refreshGrid();
        } catch (UserException | AccessDeniedException exception) {
            showError(exception.getMessage());
        }
    }

    private void requestDestructive(Runnable action) {
        boolean discardDetail = dirty && mode != FormMode.VIEW;
        requestSwitch(() -> {
            if (discardDetail) {
                selectedUser = null;
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
        username.setReadOnly(readOnly || mode != FormMode.CREATE);
        email.setReadOnly(readOnly || mode != FormMode.CREATE);
        password.setReadOnly(readOnly);
        active.setReadOnly(readOnly);
        roles.setReadOnly(readOnly || !userService.canAssignRoles());
    }

    private Set<Role> rolesFromRequest(UserRequest request) {
        return availableRoles.stream()
                .filter(role -> request.getRoleIds().contains(role.getId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    private String roleSummary(Set<Role> roles) {
        return roles.stream().map(Role::getName).sorted().collect(Collectors.joining(", "));
    }

    private String formatInstant(Instant value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    private void clearValidationErrors() {
        username.setInvalid(false);
        email.setInvalid(false);
        password.setInvalid(false);
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
