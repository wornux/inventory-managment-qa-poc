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
import com.vaadin.flow.component.icon.Icon;
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
import com.wornux.ui.security.UiAccessService;
import com.wornux.ui.views.CategoriesView;
import com.wornux.ui.views.ForbiddenView;
import com.wornux.ui.views.HomeView;
import com.wornux.ui.views.PermissionsView;
import com.wornux.ui.views.ProductsView;
import com.wornux.ui.views.RolesView;
import com.wornux.ui.views.StockMovementsView;
import com.wornux.ui.views.SuppliersView;
import com.wornux.ui.views.UsersView;
import jakarta.annotation.security.PermitAll;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.userdetails.UserDetails;

@Layout
@PermitAll
public class MainLayout extends AppLayout implements BeforeEnterObserver {

    private static final Map<Class<? extends Component>, String> ROUTE_RESOURCES = new LinkedHashMap<>();

    static {
        ROUTE_RESOURCES.put(ProductsView.class, "PRODUCT");
        ROUTE_RESOURCES.put(CategoriesView.class, "CATEGORY");
        ROUTE_RESOURCES.put(SuppliersView.class, "SUPPLIER");
        ROUTE_RESOURCES.put(StockMovementsView.class, "STOCK_MOVEMENT");
        ROUTE_RESOURCES.put(UsersView.class, "USER");
        ROUTE_RESOURCES.put(RolesView.class, "ROLE");
        ROUTE_RESOURCES.put(PermissionsView.class, "PERMISSION");
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
        String resourceCode = ROUTE_RESOURCES.get(event.getNavigationTarget());
        if (resourceCode != null && !accessService.canRead(resourceCode)) {
            event.rerouteTo(ForbiddenView.class);
        }
    }

    private Component createTopBar() {
        var toggle = new DrawerToggle();
        toggle.addClassName("main-layout-toggle");

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

        var topBar = new HorizontalLayout(toggle, title, spacer, avatar, logout);
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
        wrapper.addClassName("main-layout-nav");
        wrapper.setPadding(false);
        wrapper.setSpacing(false);

        var overview = new SideNav();
        overview.addItem(navItem("Overview", "", VaadinIcon.HOME));
        wrapper.add(overview);

        var inventory = section("Inventory");
        boolean hasInventory = false;
        hasInventory |= addIfAllowed(inventory, "Products", "products", VaadinIcon.PACKAGE, "PRODUCT");
        hasInventory |= addIfAllowed(inventory, "Categories", "categories", VaadinIcon.TAGS, "CATEGORY");
        hasInventory |= addIfAllowed(inventory, "Suppliers", "suppliers", VaadinIcon.TRUCK, "SUPPLIER");
        hasInventory |= addIfAllowed(
                inventory, "Stock Movements", "stock-movements", VaadinIcon.EXCHANGE, "STOCK_MOVEMENT");
        if (hasInventory) {
            wrapper.add(inventory);
        }

        var administration = section("Administration");
        boolean hasAdministration = false;
        hasAdministration |= addIfAllowed(administration, "Users", "users", VaadinIcon.USERS, "USER");
        hasAdministration |= addIfAllowed(administration, "Roles", "roles", VaadinIcon.KEY, "ROLE");
        hasAdministration |= addIfAllowed(administration, "Permissions", "permissions", VaadinIcon.LOCK, "PERMISSION");
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
            VaadinIcon icon,
            String resourceCode) {
        if (accessService.canRead(resourceCode)) {
            nav.addItem(navItem(label, path, icon));
            return true;
        }
        return false;
    }

    private SideNavItem navItem(String label, String path, VaadinIcon icon) {
        var item = new SideNavItem(label, path);
        item.setPrefixComponent(new Icon(icon));
        item.setMatchNested(true);
        return item;
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
        return authenticationContext.getAuthenticatedUser(UserDetails.class)
                .map(UserDetails::getUsername)
                .orElse("User");
    }
}
