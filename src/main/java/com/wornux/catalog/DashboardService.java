package com.wornux.catalog;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final int LIST_LIMIT = 5;

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuthorizationService authorizationService;
    private final MeterRegistry meterRegistry;

    public DashboardService(
            ProductRepository productRepository,
            StockMovementRepository stockMovementRepository,
            AuthorizationService authorizationService,
            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.authorizationService = authorizationService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public DashboardSnapshot getDashboard() {
        authorizationService.check(AppPermission.REPORT_VIEW);
        ProductRepository.InventorySummaryView summary = productRepository.summarizeActiveInventory();

        List<DashboardSnapshot.CriticalProduct> criticalProducts =
                productRepository.findActiveLowStockProducts(PageRequest.of(0, LIST_LIMIT)).stream()
                        .map(product -> new DashboardSnapshot.CriticalProduct(
                                product.getSku(),
                                product.getName(),
                                product.getQuantityOnHand(),
                                product.getMinimumStock()))
                        .toList();

        List<DashboardSnapshot.TopSellingProduct> topSellingProducts =
                stockMovementRepository.findTopSellingProducts(PageRequest.of(0, LIST_LIMIT)).stream()
                        .map(product -> new DashboardSnapshot.TopSellingProduct(
                                product.getSku(), product.getName(), product.getUnitsSold()))
                        .toList();

        List<DashboardSnapshot.RecentMovement> recentMovements =
                stockMovementRepository.findTop6ByOrderByCreatedDateDescIdDesc().stream()
                        .map(movement -> new DashboardSnapshot.RecentMovement(
                                movement.getProduct().getName(),
                                movement.getMovementType(),
                                movement.getQuantityDelta(),
                                movement.getUser() == null
                                        ? "System"
                                        : movement.getUser().getUsername(),
                                movement.getCreatedAt()))
                        .toList();

        List<DashboardSnapshot.MovementVolume> movementVolume =
                stockMovementRepository.findLatestMovementVolume().stream()
                        .map(volume -> new DashboardSnapshot.MovementVolume(
                                volume.getActivityDate(), volume.getInboundUnits(), volume.getOutboundUnits()))
                        .toList();

        return new DashboardSnapshot(
                summary.getActiveProducts(),
                summary.getInventoryUnits(),
                summary.getInventoryValue(),
                summary.getLowStockProducts(),
                systemMetrics(),
                criticalProducts,
                topSellingProducts,
                recentMovements,
                movementVolume);
    }

    private DashboardSnapshot.SystemMetrics systemMetrics() {
        Gauge uptime = meterRegistry.find("process.uptime").gauge();
        Collection<Timer> requestTimers =
                meterRegistry.find("http.server.requests").timers();
        long requests = requestTimers.stream().mapToLong(Timer::count).sum();
        double responseMillis = requestTimers.stream()
                .mapToDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS))
                .sum();
        long errors = requestTimers.stream()
                .filter(this::isServerError)
                .mapToLong(Timer::count)
                .sum();

        return new DashboardSnapshot.SystemMetrics(
                uptime == null ? 0 : Math.max(0, Math.round(uptime.value())),
                requests,
                requests == 0 ? 0 : responseMillis / requests,
                requests == 0 ? 0 : errors * 100.0 / requests);
    }

    private boolean isServerError(Timer timer) {
        String status = timer.getId().getTag("status");

        return status != null && status.startsWith("5");
    }
}
