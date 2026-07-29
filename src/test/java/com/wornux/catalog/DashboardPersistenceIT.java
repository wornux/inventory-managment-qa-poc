package com.wornux.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.wornux.audit.AuditConfig;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@DataJpaTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "spring.flyway.locations=classpath:db/migration/prod"
        })
@Import(AuditConfig.class)
class DashboardPersistenceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.1");

    @Autowired
    ProductRepository products;

    @Autowired
    StockMovementRepository movements;

    @Autowired
    CategoryRepository categories;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void inventoryQueries_excludeInactiveProducts() {
        Category category = category("Dashboard Inventory");
        Product critical = product("DASH-LOW", "Critical", new BigDecimal("5.00"), 2, 2, category, true);
        Product healthy = product("DASH-OK", "Healthy", new BigDecimal("3.00"), 10, 2, category, true);
        Product inactive = product("DASH-OFF", "Inactive", new BigDecimal("100.00"), 0, 2, category, false);
        products.saveAllAndFlush(List.of(critical, healthy, inactive));

        ProductRepository.InventorySummaryView summary = products.summarizeActiveInventory();
        List<Product> lowStock = products.findActiveLowStockProducts(PageRequest.of(0, 5));

        assertThat(summary.getActiveProducts()).isEqualTo(2);
        assertThat(summary.getInventoryUnits()).isEqualTo(12);
        assertThat(summary.getInventoryValue()).isEqualByComparingTo("40.00");
        assertThat(summary.getLowStockProducts()).isEqualTo(1);
        assertThat(lowStock).extracting(Product::getSku).containsExactly("DASH-LOW");
    }

    @Test
    void topSellingProducts_rankActiveSaleMovementsOnly() {
        Category category = category("Dashboard Sales");
        Product first = product("DASH-FIRST", "First", BigDecimal.ONE, 10, 1, category, true);
        Product second = product("DASH-SECOND", "Second", BigDecimal.ONE, 10, 1, category, true);
        Product inactive = product("DASH-INACTIVE", "Inactive", BigDecimal.ONE, 10, 1, category, false);
        products.saveAllAndFlush(List.of(first, second, inactive));
        movements.saveAllAndFlush(List.of(
                movement(first, MovementType.SALE, -4),
                movement(first, MovementType.SALE, -2),
                movement(first, MovementType.DAMAGED, -100),
                movement(second, MovementType.SALE, -3),
                movement(inactive, MovementType.SALE, -50)));

        List<StockMovementRepository.TopSellingProductView> topSelling =
                movements.findTopSellingProducts(PageRequest.of(0, 5));

        assertThat(topSelling)
                .extracting(
                        StockMovementRepository.TopSellingProductView::getSku,
                        StockMovementRepository.TopSellingProductView::getUnitsSold)
                .containsExactly(tuple("DASH-FIRST", 6L), tuple("DASH-SECOND", 3L));
    }

    @Test
    void movementQueries_enforceDashboardWindowsAndOrdering() {
        Category category = category("Dashboard History");
        Product product =
                products.saveAndFlush(product("DASH-HISTORY", "History", BigDecimal.ONE, 100, 1, category, true));
        List<StockMovement> history = IntStream.rangeClosed(1, 8)
                .mapToObj(day -> movement(product, MovementType.PURCHASE, day))
                .toList();
        movements.saveAllAndFlush(history);
        for (int day = 1; day <= history.size(); day++) {
            setCreatedAt(history.get(day - 1), "2026-03-%02dT10:00:00Z".formatted(day));
        }
        entityManager.clear();

        List<StockMovementRepository.MovementVolumeView> volume = movements.findLatestMovementVolume();
        List<StockMovement> recent = movements.findTop6ByOrderByCreatedDateDescIdDesc();

        assertThat(volume)
                .extracting(
                        StockMovementRepository.MovementVolumeView::getActivityDate,
                        StockMovementRepository.MovementVolumeView::getInboundUnits,
                        StockMovementRepository.MovementVolumeView::getOutboundUnits)
                .containsExactly(
                        tuple(LocalDate.of(2026, 3, 2), 2L, 0L),
                        tuple(LocalDate.of(2026, 3, 3), 3L, 0L),
                        tuple(LocalDate.of(2026, 3, 4), 4L, 0L),
                        tuple(LocalDate.of(2026, 3, 5), 5L, 0L),
                        tuple(LocalDate.of(2026, 3, 6), 6L, 0L),
                        tuple(LocalDate.of(2026, 3, 7), 7L, 0L),
                        tuple(LocalDate.of(2026, 3, 8), 8L, 0L));
        assertThat(recent)
                .extracting(StockMovement::getCreatedAt)
                .containsExactly(
                        Instant.parse("2026-03-08T10:00:00Z"),
                        Instant.parse("2026-03-07T10:00:00Z"),
                        Instant.parse("2026-03-06T10:00:00Z"),
                        Instant.parse("2026-03-05T10:00:00Z"),
                        Instant.parse("2026-03-04T10:00:00Z"),
                        Instant.parse("2026-03-03T10:00:00Z"));
    }

    @Test
    void dashboardMigration_grantsReportAccessToBuiltInRoles() {
        Long rolesWithDashboardAccess = jdbc.queryForObject("""
                select count(*)
                from role
                where code in (
                    'SYSTEM_ADMINISTRATOR',
                    'INVENTORY_MANAGER',
                    'WAREHOUSE_OPERATOR',
                    'INVENTORY_VIEWER'
                )
                and 'report:view' = any(permissions)
                """, Long.class);

        assertThat(rolesWithDashboardAccess).isEqualTo(4);
    }

    private Category category(String name) {
        return categories.saveAndFlush(new Category(name, null));
    }

    private Product product(
            String sku,
            String name,
            BigDecimal unitPrice,
            int quantity,
            int minimumStock,
            Category category,
            boolean active) {
        return new Product(sku, name, null, unitPrice, quantity, minimumStock, category, null, active);
    }

    private StockMovement movement(Product product, MovementType type, int quantity) {
        return new StockMovement(product, null, type, quantity, null);
    }

    private void setCreatedAt(StockMovement movement, String createdAt) {
        entityManager
                .createNativeQuery("update stock_movement set created_date = :createdAt where id = :id")
                .setParameter("createdAt", Instant.parse(createdAt))
                .setParameter("id", movement.getId())
                .executeUpdate();
    }
}
