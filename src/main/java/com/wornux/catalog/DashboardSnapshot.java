package com.wornux.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DashboardSnapshot(
        long activeProducts,
        long inventoryUnits,
        BigDecimal inventoryValue,
        long lowStockProducts,
        SystemMetrics systemMetrics,
        List<CriticalProduct> criticalProducts,
        List<TopSellingProduct> topSellingProducts,
        List<RecentMovement> recentMovements,
        List<MovementVolume> movementVolume) {

    public record SystemMetrics(
            long uptimeSeconds, long requests, double averageResponseMillis, double errorRatePercent) {}

    public record CriticalProduct(String sku, String name, int quantityOnHand, int minimumStock) {}

    public record TopSellingProduct(String sku, String name, long unitsSold) {}

    public record RecentMovement(
            String productName, MovementType movementType, int quantityDelta, String username, Instant createdAt) {}

    public record MovementVolume(LocalDate activityDate, long inboundUnits, long outboundUnits) {}
}
