package com.wornux.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.catalog.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class InventoryMetricsTest {

    @Test
    void gaugeReadsCurrentLowStockCountFromRepository() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.countActiveLowStockProducts()).thenReturn(4L);
        var registry = new SimpleMeterRegistry();

        new InventoryMetrics(products).bindTo(registry);

        assertThat(registry.get("wornux.inventory.low.stock.products").gauge().value())
                .isEqualTo(4.0);
        verify(products).countActiveLowStockProducts();
    }
}
