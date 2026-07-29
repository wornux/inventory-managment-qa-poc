package com.wornux.observability;

import com.wornux.catalog.ProductRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetrics implements MeterBinder {

    private final ProductRepository productRepository;

    public InventoryMetrics(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        Gauge.builder(
                        "wornux.inventory.low.stock.products",
                        productRepository,
                        ProductRepository::countActiveLowStockProducts)
                .description("Active products at or below their minimum stock")
                .register(registry);
    }
}
