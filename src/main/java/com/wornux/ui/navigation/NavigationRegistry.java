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
import java.util.List;
import java.util.Optional;

public final class NavigationRegistry {

    private static final List<NavigationEntry> ENTRIES = List.of(
            new NavigationEntry(null, "Overview", HomeView.class, "/icons/overview.svg", AppPermission.REPORT_VIEW),
            new NavigationEntry(
                    "Inventory", "Products", ProductsView.class, "/icons/package.svg", AppPermission.PRODUCT_VIEW),
            new NavigationEntry(
                    "Inventory",
                    "Categories",
                    CategoriesView.class,
                    "/icons/categories.svg",
                    AppPermission.CATEGORY_VIEW),
            new NavigationEntry(
                    "Inventory", "Suppliers", SuppliersView.class, "/icons/suppliers.svg", AppPermission.SUPPLIER_VIEW),
            new NavigationEntry(
                    "Inventory",
                    "Stock Movements",
                    StockMovementsView.class,
                    "/icons/stock-movement.svg",
                    AppPermission.STOCK_MOVEMENT_VIEW),
            new NavigationEntry(
                    "Administration", "Users", UsersView.class, "/icons/users.svg", AppPermission.USER_VIEW),
            new NavigationEntry(
                    "Administration", "Roles", RolesView.class, "/icons/roles.svg", AppPermission.ROLE_VIEW));

    private NavigationRegistry() {}

    public static List<NavigationEntry> entries() {
        return ENTRIES;
    }

    public static Optional<NavigationEntry> findByTarget(Class<? extends Component> target) {
        return ENTRIES.stream().filter(entry -> entry.target().equals(target)).findFirst();
    }
}
