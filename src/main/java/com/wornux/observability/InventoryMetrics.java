package com.wornux.observability;

import com.wornux.catalog.ProductRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetrics implements MeterBinder {

    private final ProductRepository productRepository;

    public InventoryMetrics(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(
                        "wornux.inventory.low.stock.products",
                        productRepository,
                        repository -> repository.countActiveLowStockProducts())
                .description("Active products at or below their minimum stock")
                .register(registry);
    }
}
