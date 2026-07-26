package com.wornux.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.wornux.audit.AuditConfig;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/prod"
})
@Import({
        AuditConfig.class,
        ProductService.class,
        StockMovementService.class
})
class StockMovementIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.1");

    @MockitoBean
    AuthorizationService authorizationService;

    @Autowired
    StockMovementService stockMovementService;

    @Autowired
    ProductService productService;

    @Autowired
    StockMovementRepository stockMovementRepository;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void recordPurchase_increasesProductStockAndPersistsMovement() {
        Product product = persistProduct(5);
        Long productId = product.getId();
        StockMovementRequest request =
                request(productId, MovementType.PURCHASE, 3, "Purchase from supplier");

        StockMovement recordedMovement = stockMovementService.recordStockMovement(request);
        Long movementId = recordedMovement.getId();

        flushAndClear();
        Product persistedProduct = productRepository.findById(productId).orElseThrow();
        StockMovement persistedMovement = stockMovementRepository.findById(movementId).orElseThrow();

        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(8);
        assertThat(persistedMovement.getProduct().getId()).isEqualTo(productId);
        assertThat(persistedMovement.getMovementType()).isEqualTo(MovementType.PURCHASE);
        assertThat(persistedMovement.getQuantityDelta()).isEqualTo(3);
        assertThat(persistedMovement.getReason()).isEqualTo("Purchase from supplier");
        verify(authorizationService).check(AppPermission.STOCK_MOVEMENT_CREATE);
    }

    @Test
    void recordSale_reachingMinimumStockAppearsInLowStockResults() {
        Product product = persistProduct(5);
        Long productId = product.getId();
        StockMovementRequest request = request(productId, MovementType.SALE, -3, null);

        stockMovementService.recordStockMovement(request);

        flushAndClear();
        Product persistedProduct = productRepository.findById(productId).orElseThrow();
        List<Product> lowStockProducts = productService
                .search(new ProductFilter("", null, null, true, true), PageRequest.of(0, 10))
                .getContent();

        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(2);
        assertThat(persistedProduct.isLowStock()).isTrue();
        assertThat(persistedProduct.getStockStatus()).isEqualTo("LOW STOCK");
        assertThat(lowStockProducts).extracting(Product::getId).containsExactly(productId);
        verify(authorizationService).check(AppPermission.STOCK_MOVEMENT_CREATE);
        verify(authorizationService).check(AppPermission.PRODUCT_VIEW);
    }

    @Test
    void recordSale_exceedingAvailableStockKeepsStockAndDoesNotPersistMovement() {
        Product product = persistProduct(2);
        Long productId = product.getId();
        StockMovementRequest request =
                request(productId, MovementType.SALE, -3, null);

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(request))
                .isInstanceOf(StockMovementException.class)
                .hasMessageContaining("Insufficient stock");

        flushAndClear();

        Product persistedProduct = productRepository
                .findById(productId)
                .orElseThrow();

        assertThat(persistedProduct.getQuantityOnHand()).isEqualTo(2);
        assertThat(stockMovementRepository.existsByProductId(productId)).isFalse();

        verify(authorizationService).check(AppPermission.STOCK_MOVEMENT_CREATE);
    }

    private Product persistProduct(int quantityOnHand) {
        Category category = categoryRepository.saveAndFlush(
                new Category("Integration Tools", "Category for stock movement tests"));

        return productRepository.saveAndFlush(new Product(
                "IT-HAMMER-001",
                "Integration Hammer",
                "Product used in stock movement tests",
                new BigDecimal("25.00"),
                quantityOnHand,
                2,
                category,
                null,
                true));
    }

    private static StockMovementRequest request(
            Long productId, MovementType movementType, int quantityDelta, String reason) {
        StockMovementRequest request = new StockMovementRequest();
        request.setProductId(productId);
        request.setMovementType(movementType);
        request.setQuantityDelta(quantityDelta);
        request.setReason(reason);

        return request;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
