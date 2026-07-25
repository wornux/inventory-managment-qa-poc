package com.wornux.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.components.DrawerRailToggle;
import com.wornux.ui.security.UiAccessService;
import com.wornux.ui.views.CategoriesView;
import com.wornux.ui.views.ForbiddenView;
import com.wornux.ui.views.ProductsView;
import com.wornux.ui.views.RolesView;
import com.wornux.ui.views.StockMovementsView;
import com.wornux.ui.views.SuppliersView;
import com.wornux.ui.views.UsersView;
import jakarta.annotation.security.PermitAll;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@Layout
@PermitAll
public class MainLayout extends AppLayout implements BeforeEnterObserver {

    private static final Map<Class<? extends Component>, AppPermission> ROUTE_PERMISSIONS = new LinkedHashMap<>();

    static {
        ROUTE_PERMISSIONS.put(ProductsView.class, AppPermission.PRODUCT_VIEW);
        ROUTE_PERMISSIONS.put(CategoriesView.class, AppPermission.CATEGORY_VIEW);
        ROUTE_PERMISSIONS.put(SuppliersView.class, AppPermission.SUPPLIER_VIEW);
        ROUTE_PERMISSIONS.put(StockMovementsView.class, AppPermission.STOCK_MOVEMENT_VIEW);
        ROUTE_PERMISSIONS.put(UsersView.class, AppPermission.USER_VIEW);
        ROUTE_PERMISSIONS.put(RolesView.class, AppPermission.ROLE_VIEW);
    }

    private final transient AuthenticationContext authenticationContext;
    private final UiAccessService accessService;

    public MainLayout(AuthenticationContext authenticationContext, UiAccessService accessService) {
        this.authenticationContext = authenticationContext;
        this.accessService = accessService;

        addClassName("main-layout");
        setPrimarySection(Section.DRAWER);
        addToNavbar(createTopBar());
        addToDrawer(createDrawerHeader(), new Scroller(createNavigation()), createDrawerFooter());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        AppPermission permission = ROUTE_PERMISSIONS.get(event.getNavigationTarget());
        if (permission != null && !accessService.canRead(permission)) {
            event.rerouteTo(ForbiddenView.class);
        }
    }

    private Component createTopBar() {
        var mobileToggle = new DrawerToggle();
        mobileToggle.addClassName("main-layout-toggle");
        mobileToggle.setAriaLabel("Open navigation");

        var railToggle = new DrawerRailToggle();

        var title = new H1("Inventory");
        title.addClassName("main-layout-title");

        var spacer = new Div();
        spacer.addClassName("main-layout-spacer");

        var user = currentUsername();
        var avatar = new Avatar(user);
        avatar.addClassName("main-layout-avatar");

        var logout = new Button("Sign out", VaadinIcon.SIGN_OUT.create(), event -> authenticationContext.logout());
        logout.addThemeVariants(ButtonVariant.TERTIARY);
        logout.addClassName("main-layout-logout");

        var topBar = new HorizontalLayout(mobileToggle, railToggle, title, spacer, avatar, logout);
        topBar.addClassName("main-layout-topbar");
        topBar.setAlignItems(FlexComponent.Alignment.CENTER);
        topBar.setWidthFull();
        return topBar;
    }

    private Component createDrawerHeader() {
        var mark = new Span("W");
        mark.addClassName("main-layout-brand-mark");

        var name = new Span("Wornux");
        name.addClassName("main-layout-brand-name");

        var label = new Span("Inventory Ops");
        label.addClassName("main-layout-brand-label");

        var copy = new VerticalLayout(name, label);
        copy.addClassName("main-layout-brand-copy");
        copy.setPadding(false);
        copy.setSpacing(false);

        var header = new HorizontalLayout(mark, copy);
        header.addClassName("main-layout-brand");
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        return header;
    }

    private Component createNavigation() {
        var wrapper = new VerticalLayout();
        wrapper.addClassNames("main-layout-nav", "main-layout-icon-carousel");
        wrapper.setPadding(false);
        wrapper.setSpacing(false);

        var overview = new SideNav();
        overview.addItem(navItem("Overview", "", svgIcon("/icons/overview.svg")));
        wrapper.add(overview);

        var inventory = section("Inventory");
        boolean hasInventory = false;
        hasInventory |= addIfAllowed(inventory, "Products", "products", svgIcon("/icons/package.svg"), AppPermission.PRODUCT_VIEW);
        hasInventory |= addIfAllowed(
                inventory, "Categories", "categories", svgIcon("/icons/categories.svg"), AppPermission.CATEGORY_VIEW);
        hasInventory |= addIfAllowed(
                inventory, "Suppliers", "suppliers", svgIcon("/icons/suppliers.svg"), AppPermission.SUPPLIER_VIEW);
        hasInventory |= addIfAllowed(
                inventory,
                "Stock Movements",
                "stock-movements",
                svgIcon("/icons/stock-movement.svg"),
                AppPermission.STOCK_MOVEMENT_VIEW);
        if (hasInventory) {
            wrapper.add(inventory);
        }

        var administration = section("Administration");
        boolean hasAdministration = false;
        hasAdministration |= addIfAllowed(
                administration, "Users", "users", svgIcon("/icons/users.svg"), AppPermission.USER_VIEW);
        hasAdministration |= addIfAllowed(administration, "Roles", "roles", svgIcon("/icons/roles.svg"), AppPermission.ROLE_VIEW);
        if (hasAdministration) {
            wrapper.add(administration);
        }

        if (!hasInventory && !hasAdministration) {
            var empty = new Span("No modules available");
            empty.addClassName("main-layout-empty-nav");
            wrapper.add(empty);
        }

        return wrapper;
    }

    private SideNav section(String label) {
        var nav = new SideNav(label);
        nav.addClassName("main-layout-nav-section");
        return nav;
    }

    private boolean addIfAllowed(
            SideNav nav,
            String label,
            String path,
            Component icon,
            AppPermission permission) {
        if (accessService.canRead(permission)) {
            nav.addItem(navItem(label, path, icon));
            return true;
        }
        return false;
    }

    private SideNavItem navItem(String label, String path, Component icon) {
        var item = new SideNavItem(label, path);
        item.setPrefixComponent(icon);
        item.setMatchNested(true);
        return item;
    }

    private SvgIcon svgIcon(String path) {
        var icon = new SvgIcon(path);
        icon.addClassName("main-layout-custom-icon");
        return icon;
    }

    private Component createDrawerFooter() {
        var user = new Span(currentUsername());
        user.addClassName("main-layout-user-name");

        var role = new Span("Signed in");
        role.addClassName("main-layout-user-role");

        var avatar = new Avatar(currentUsername());
        avatar.addClassName("main-layout-footer-avatar");

        var copy = new VerticalLayout(user, role);
        copy.addClassName("main-layout-user-copy");
        copy.setPadding(false);
        copy.setSpacing(false);

        var footer = new HorizontalLayout(avatar, copy);
        footer.addClassName("main-layout-user");
        footer.setAlignItems(FlexComponent.Alignment.CENTER);
        return footer;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "User";
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            String username = oidcUser.getClaimAsString("preferred_username");
            return username == null || username.isBlank() ? oidcUser.getName() : username;
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return authentication.getName() == null || authentication.getName().isBlank()
                ? "User"
                : authentication.getName();
    }
}
