package com.wornux.ui.navigation;

import com.vaadin.flow.component.Component;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.views.CategoriesView;
import com.wornux.ui.views.HomeView;
import com.wornux.ui.views.ProductsView;
import com.wornux.ui.views.RolesView;
import com.wornux.ui.views.StockMovementsView;
import com.wornux.ui.views.SuppliersView;
import com.wornux.ui.views.UsersView;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class NavigationRegistry {

    private static final List<NavigationEntry> ENTRIES = List.of(
                    new NavigationEntry(
                            null, "Overview", HomeView.class, "/icons/overview.svg", AppPermission.REPORT_VIEW, 0),
                    new NavigationEntry(
                            "Inventory",
                            "Products",
                            ProductsView.class,
                            "/icons/package.svg",
                            AppPermission.PRODUCT_VIEW,
                            10),
                    new NavigationEntry(
                            "Inventory",
                            "Categories",
                            CategoriesView.class,
                            "/icons/categories.svg",
                            AppPermission.CATEGORY_VIEW,
                            20),
                    new NavigationEntry(
                            "Inventory",
                            "Suppliers",
                            SuppliersView.class,
                            "/icons/suppliers.svg",
                            AppPermission.SUPPLIER_VIEW,
                            30),
                    new NavigationEntry(
                            "Inventory",
                            "Stock Movements",
                            StockMovementsView.class,
                            "/icons/stock-movement.svg",
                            AppPermission.STOCK_MOVEMENT_VIEW,
                            40),
                    new NavigationEntry(
                            "Administration",
                            "Users",
                            UsersView.class,
                            "/icons/users.svg",
                            AppPermission.USER_VIEW,
                            50),
                    new NavigationEntry(
                            "Administration",
                            "Roles",
                            RolesView.class,
                            "/icons/roles.svg",
                            AppPermission.ROLE_VIEW,
                            60))
            .stream()
            .sorted(Comparator.comparingInt(NavigationEntry::order))
            .toList();

    private NavigationRegistry() {}

    public static List<NavigationEntry> entries() {
        return ENTRIES;
    }

    public static Optional<NavigationEntry> findByTarget(Class<? extends Component> target) {
        return ENTRIES.stream().filter(entry -> entry.target().equals(target)).findFirst();
    }
}
