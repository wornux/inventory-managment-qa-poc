package com.wornux.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Section;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.wornux.catalog.DashboardService;
import com.wornux.catalog.DashboardSnapshot;
import com.wornux.security.permission.AppPermission;
import com.wornux.ui.components.InventoryMovementChart;
import com.wornux.ui.security.UiAccessService;
import jakarta.annotation.security.PermitAll;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Route("")
@PageTitle("Dashboard")
@PermitAll
public class HomeView extends Main {

    private static final DateTimeFormatter ACTIVITY_DATE = DateTimeFormatter.ofPattern("MMM d, yy", Locale.ENGLISH);
    private static final DateTimeFormatter MOVEMENT_DATE =
            DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final UiAccessService accessService;

    public HomeView(DashboardService dashboardService, UiAccessService accessService) {
        this.accessService = accessService;
        setId("dashboard-view");
        addClassName("home-view");

        if (!accessService.canRead(AppPermission.REPORT_VIEW)) {
            return;
        }

        DashboardSnapshot dashboard = dashboardService.getDashboard();
        add(
                header(),
                kpis(dashboard),
                systemMetrics(dashboard.systemMetrics()),
                operationalOverview(dashboard),
                activityOverview(dashboard));
    }

    private Component header() {
        var title = new H1("Inventory dashboard");
        var subtitle = new Paragraph("Current stock health, sales velocity, and the latest warehouse activity.");
        subtitle.addClassName("home-subtitle");

        var header = new Header(title, subtitle);
        header.addClassName("dashboard-header");

        return header;
    }

    private Component kpis(DashboardSnapshot dashboard) {
        var metrics = new Div(
                kpi("Active products", number(dashboard.activeProducts()), "Available in the catalog"),
                kpi("Units on hand", number(dashboard.inventoryUnits()), "Across active products"),
                kpi("Inventory value", decimal(dashboard.inventoryValue()), "At current unit prices"),
                kpi(
                        "Critical products",
                        number(dashboard.lowStockProducts()),
                        dashboard.lowStockProducts() == 1 ? "Needs attention" : "Need attention"));
        metrics.addClassName("dashboard-kpis");

        return metrics;
    }

    private Component systemMetrics(DashboardSnapshot.SystemMetrics metrics) {
        var values = new Div(
                systemMetric("Uptime", uptime(metrics.uptimeSeconds())),
                systemMetric("Requests", number(metrics.requests())),
                systemMetric("Average response", String.format(Locale.US, "%,.0f ms", metrics.averageResponseMillis())),
                systemMetric("Server error rate", String.format(Locale.US, "%.1f%%", metrics.errorRatePercent())));
        values.addClassName("dashboard-system-metrics");

        return panel("System metrics", "Runtime signals since this application instance started.", values, null);
    }

    private Component operationalOverview(DashboardSnapshot dashboard) {
        var chart = panel(
                "Movement volume",
                "Inbound and outbound units across the latest seven activity days.",
                movementChart(dashboard.movementVolume()),
                null);

        var critical = panel(
                "Critical stock",
                "Active products at or below their minimum threshold.",
                criticalProducts(dashboard.criticalProducts()),
                permittedLink(AppPermission.PRODUCT_VIEW, "View products", ProductsView.class));

        var grid = new Div(chart, critical);
        grid.addClassName("dashboard-operational-grid");

        return grid;
    }

    private Component activityOverview(DashboardSnapshot dashboard) {
        var topSelling = panel(
                "Top selling",
                "Products ranked by units recorded as sales.",
                topSellingProducts(dashboard.topSellingProducts()),
                permittedLink(AppPermission.PRODUCT_VIEW, "View products", ProductsView.class));
        var recent = panel(
                "Recent movements",
                "The newest entries in the stock ledger.",
                recentMovements(dashboard.recentMovements()),
                permittedLink(AppPermission.STOCK_MOVEMENT_VIEW, "View ledger", StockMovementsView.class));

        var grid = new Div(topSelling, recent);
        grid.addClassName("dashboard-activity-grid");

        return grid;
    }

    private Component movementChart(List<DashboardSnapshot.MovementVolume> volume) {
        if (volume.isEmpty()) {
            return emptyState("Movement activity will appear after the first stock entry.");
        }

        var chart = new InventoryMovementChart();
        chart.setData(volume.stream()
                .map(day -> new InventoryMovementChart.MovementPoint(
                        ACTIVITY_DATE.format(day.activityDate()), day.inboundUnits(), day.outboundUnits()))
                .toList());

        return chart;
    }

    private Component criticalProducts(List<DashboardSnapshot.CriticalProduct> products) {
        if (products.isEmpty()) {
            return emptyState("All active products are above their minimum stock.");
        }

        var list = list();
        products.forEach(product -> {
            var identity = identity(product.name(), product.sku());
            var quantity = new Div(
                    strong(number(product.quantityOnHand())),
                    secondary(" / " + number(product.minimumStock()) + " min"));
            quantity.addClassName("dashboard-list-value");
            list.add(item(identity, quantity));
        });

        return list;
    }

    private Component topSellingProducts(List<DashboardSnapshot.TopSellingProduct> products) {
        if (products.isEmpty()) {
            return emptyState("Sales rankings will appear after sale movements are recorded.");
        }

        var list = list();
        products.forEach(product -> {
            var identity = identity(product.name(), product.sku());
            var units = new Div(strong(number(product.unitsSold())), secondary(" units"));
            units.addClassName("dashboard-list-value");
            list.add(item(identity, units));
        });

        return list;
    }

    private Component recentMovements(List<DashboardSnapshot.RecentMovement> movements) {
        if (movements.isEmpty()) {
            return emptyState("Stock movements will appear here as they are recorded.");
        }

        var list = list();
        movements.forEach(movement -> {
            var identity = identity(
                    movement.productName(),
                    movement.movementType().displayName()
                            + " · "
                            + movement.username()
                            + " · "
                            + formatDate(movement.createdAt()));
            var quantity = new Div(strong(signed(movement.quantityDelta())), secondary(" units"));
            quantity.addClassNames(
                    "dashboard-list-value", movement.quantityDelta() > 0 ? "quantity-positive" : "quantity-negative");
            list.add(item(identity, quantity));
        });

        return list;
    }

    private Div systemMetric(String label, String value) {
        var labelText = new Span(label);
        labelText.addClassName("dashboard-system-label");
        var valueText = new Span(value);
        valueText.addClassName("dashboard-system-value");

        var metric = new Div(labelText, valueText);
        metric.addClassName("dashboard-system-metric");

        return metric;
    }

    private Div kpi(String label, String value, String detail) {
        var labelText = new Span(label);
        labelText.addClassName("dashboard-kpi-label");
        var valueText = new Span(value);
        valueText.addClassName("dashboard-kpi-value");
        var detailText = new Span(detail);
        detailText.addClassName("dashboard-kpi-detail");

        var metric = new Div(labelText, valueText, detailText);
        metric.addClassName("dashboard-kpi");

        return metric;
    }

    private RouterLink permittedLink(
            AppPermission permission, String label, Class<? extends Component> navigationTarget) {
        return accessService.canRead(permission) ? new RouterLink(label, navigationTarget) : null;
    }

    private Section panel(String title, String description, Component body, RouterLink link) {
        var heading = new H2(title);
        var descriptionText = new Paragraph(description);
        descriptionText.addClassName("dashboard-panel-description");
        var copy = new Div(heading, descriptionText);
        copy.addClassName("dashboard-panel-copy");

        var header = new Div(copy);
        header.addClassName("dashboard-panel-header");
        if (link != null) {
            link.addClassName("dashboard-panel-link");
            header.add(link);
        }

        var panel = new Section(header, body);
        panel.addClassName("dashboard-panel");

        return panel;
    }

    private Div list() {
        var list = new Div();
        list.addClassName("dashboard-list");
        list.getElement().setAttribute("role", "list");

        return list;
    }

    private Div item(Component... content) {
        var item = new Div(content);
        item.addClassName("dashboard-list-item");
        item.getElement().setAttribute("role", "listitem");

        return item;
    }

    private Div identity(String name, String detail) {
        var nameText = new Span(name);
        nameText.addClassName("dashboard-list-name");
        var detailText = new Span(detail);
        detailText.addClassName("dashboard-list-detail");

        var identity = new Div(nameText, detailText);
        identity.addClassName("dashboard-list-identity");

        return identity;
    }

    private Component emptyState(String message) {
        var empty = new Paragraph(message);
        empty.addClassName("dashboard-empty");

        return empty;
    }

    private Span strong(String value) {
        var text = new Span(value);
        text.addClassName("dashboard-list-strong");

        return text;
    }

    private Span secondary(String value) {
        var text = new Span(value);
        text.addClassName("dashboard-list-secondary");

        return text;
    }

    private String formatDate(Instant value) {
        return value == null ? "Pending" : MOVEMENT_DATE.format(value);
    }

    private String signed(int value) {
        return value > 0 ? "+" + number(value) : number(value);
    }

    private String uptime(long seconds) {
        return String.format(Locale.US, "%dd %02dh %02dm", seconds / 86_400, seconds / 3_600 % 24, seconds / 60 % 60);
    }

    private String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private String decimal(BigDecimal value) {
        return String.format(Locale.US, "%,.2f", value);
    }
}
