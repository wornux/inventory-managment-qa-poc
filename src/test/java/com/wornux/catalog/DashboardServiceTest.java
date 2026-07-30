package com.wornux.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    ProductRepository products;

    @Mock
    StockMovementRepository movements;

    @Mock
    AuthorizationService authorization;

    DashboardService service;
    SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new DashboardService(products, movements, authorization, meterRegistry);
    }

    @Test
    void getDashboard_mapsTheBoundedInventorySnapshot() {
        ProductRepository.InventorySummaryView summary = mock(ProductRepository.InventorySummaryView.class);
        when(summary.getActiveProducts()).thenReturn(12L);
        when(summary.getInventoryUnits()).thenReturn(345L);
        when(summary.getInventoryValue()).thenReturn(new BigDecimal("9876.54"));
        when(summary.getLowStockProducts()).thenReturn(2L);
        when(products.summarizeActiveInventory()).thenReturn(summary);

        Product critical = new Product(
                "LOW-1", "Low stock", null, new BigDecimal("5.00"), 2, 2, new Category("Tools", null), null, true);
        when(products.findActiveLowStockProducts(PageRequest.of(0, 5))).thenReturn(List.of(critical));

        StockMovementRepository.TopSellingProductView top = mock(StockMovementRepository.TopSellingProductView.class);
        when(top.getSku()).thenReturn("TOP-1");
        when(top.getName()).thenReturn("Top seller");
        when(top.getUnitsSold()).thenReturn(42L);
        when(movements.findTopSellingProducts(PageRequest.of(0, 5))).thenReturn(List.of(top));

        var user = new AppUser("operator", "operator@example.test", null, null);
        var recent = new StockMovement(critical, user, MovementType.SALE, -3, null);
        Instant createdAt = Instant.parse("2026-03-01T10:15:00Z");
        recent.setCreatedDate(createdAt);
        var systemMovement = new StockMovement(critical, null, MovementType.PURCHASE, 2, null);
        Instant systemCreatedAt = Instant.parse("2026-03-01T09:00:00Z");
        systemMovement.setCreatedDate(systemCreatedAt);
        when(movements.findTop6ByOrderByCreatedDateDescIdDesc()).thenReturn(List.of(recent, systemMovement));

        StockMovementRepository.MovementVolumeView volume = mock(StockMovementRepository.MovementVolumeView.class);
        when(volume.getActivityDate()).thenReturn(LocalDate.of(2026, 3, 1));
        when(volume.getInboundUnits()).thenReturn(20L);
        when(volume.getOutboundUnits()).thenReturn(9L);
        when(movements.findLatestMovementVolume()).thenReturn(List.of(volume));

        Gauge.builder("process.uptime", () -> 3660).register(meterRegistry);
        meterRegistry.timer("http.server.requests", "status", "200").record(100, TimeUnit.MILLISECONDS);
        meterRegistry.timer("http.server.requests", "status", "500").record(300, TimeUnit.MILLISECONDS);
        meterRegistry.timer("http.server.requests", "uri", "unknown").record(200, TimeUnit.MILLISECONDS);

        DashboardSnapshot result = service.getDashboard();

        assertThat(result)
                .isEqualTo(new DashboardSnapshot(
                        12,
                        345,
                        new BigDecimal("9876.54"),
                        2,
                        new DashboardSnapshot.SystemMetrics(3660, 3, 200, 100.0 / 3),
                        List.of(new DashboardSnapshot.CriticalProduct("LOW-1", "Low stock", 2, 2)),
                        List.of(new DashboardSnapshot.TopSellingProduct("TOP-1", "Top seller", 42)),
                        List.of(
                                new DashboardSnapshot.RecentMovement(
                                        "Low stock", MovementType.SALE, -3, "operator", createdAt),
                                new DashboardSnapshot.RecentMovement(
                                        "Low stock", MovementType.PURCHASE, 2, "System", systemCreatedAt)),
                        List.of(new DashboardSnapshot.MovementVolume(LocalDate.of(2026, 3, 1), 20, 9))));
        verify(authorization).check(AppPermission.REPORT_VIEW);
    }

    @Test
    void getDashboard_withoutRegisteredMeters_returnsZeroSystemMetrics() {
        ProductRepository.InventorySummaryView summary = mock(ProductRepository.InventorySummaryView.class);
        when(summary.getInventoryValue()).thenReturn(BigDecimal.ZERO);
        when(products.summarizeActiveInventory()).thenReturn(summary);

        DashboardSnapshot result = service.getDashboard();

        assertThat(result.systemMetrics()).isEqualTo(new DashboardSnapshot.SystemMetrics(0, 0, 0, 0));
    }

    @Test
    void getDashboard_withoutReportPermission_readsNothing() {
        doThrow(new AccessDeniedException("denied")).when(authorization).check(AppPermission.REPORT_VIEW);

        assertThatThrownBy(service::getDashboard).isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(products, movements);
    }
}
