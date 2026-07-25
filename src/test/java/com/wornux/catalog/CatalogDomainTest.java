package com.wornux.catalog;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CatalogDomainTest {
    @Test
    void persistenceConstructorsProvideSafeEntityDefaults() {
        Category category = new Category();
        Supplier supplier = new Supplier();
        Product product = new Product();
        StockMovement movement = new StockMovement();

        assertThat(category.isActive()).isTrue();
        assertThat(supplier.isActive()).isTrue();
        assertThat(product.getUnitPrice()).isZero();
        assertThat(product.getQuantityOnHand()).isZero();
        assertThat(product.getMinimumStock()).isZero();
        assertThat(product.isActive()).isTrue();
        assertThat(movement.getId()).isNull();
        assertThat(movement.getProduct()).isNull();
        assertThat(movement.getUser()).isNull();
        assertThat(movement.getMovementType()).isNull();
        assertThat(movement.getQuantityDelta()).isNull();
        assertThat(movement.getReason()).isNull();
        assertThat(movement.getCreatedAt()).isNull();
    }

    @Test
    void productTracksInventoryThresholdAndLifecycle() {
        Category category = new Category("Tools", "Useful");
        Supplier supplier = new Supplier("Acme", "Joe", "a@b.com", "123");
        Product product = new Product("SKU", "Hammer", "Steel", BigDecimal.TEN, 3, 3, category, supplier, true);

        assertThat(product.getSku()).isEqualTo("SKU");
        assertThat(product.getName()).isEqualTo("Hammer");
        assertThat(product.getDescription()).isEqualTo("Steel");
        assertThat(product.getUnitPrice()).isEqualByComparingTo("10");
        assertThat(product.getQuantityOnHand()).isEqualTo(3);
        assertThat(product.getMinimumStock()).isEqualTo(3);
        assertThat(product.getCategory()).isSameAs(category);
        assertThat(product.getSupplier()).isSameAs(supplier);
        assertThat(product.isLowStock()).isTrue();
        assertThat(product.getStockStatus()).isEqualTo("LOW STOCK");

        product.applyQuantityDelta(1);

        assertThat(product.isLowStock()).isFalse();
        assertThat(product.getStockStatus()).isEqualTo("OK");

        product.deactivate();

        assertThat(product.isActive()).isFalse();
        assertThat(product.getId()).isNull();
        assertThat(product.getVersion()).isNull();
        assertThat(product.getCreatedAt()).isNull();
        assertThat(product.getUpdatedAt()).isNull();
    }

    @Test
    void categoryAndSupplierUpdatesAreAtomicDomainChanges() {
        Category category = new Category("Old", "Old description");

        category.update("New", "New description", false);

        assertThat(category.getName()).isEqualTo("New");
        assertThat(category.getDescription()).isEqualTo("New description");
        assertThat(category.isActive()).isFalse();

        category.deactivate();

        assertThat(category.getId()).isNull();
        assertThat(category.getVersion()).isNull();
        assertThat(category.getCreatedAt()).isNull();
        assertThat(category.getUpdatedAt()).isNull();

        Supplier supplier = new Supplier("Old", "A", "a@b.com", "1");

        supplier.update("New", "B", "b@c.com", "2", false);

        assertThat(supplier.getName()).isEqualTo("New");
        assertThat(supplier.getContactName()).isEqualTo("B");
        assertThat(supplier.getEmail()).isEqualTo("b@c.com");
        assertThat(supplier.getPhone()).isEqualTo("2");

        supplier.deactivate();

        assertThat(supplier.isActive()).isFalse();
        assertThat(supplier.getId()).isNull();
        assertThat(supplier.getVersion()).isNull();
        assertThat(supplier.getCreatedAt()).isNull();
        assertThat(supplier.getUpdatedAt()).isNull();
    }

    @Test
    void movementTypesDefineDirectionReasonAndReadableName() {
        assertThat(MovementType.values()).allSatisfy(type -> {
            assertThat(type.isNegative()).isEqualTo(!type.isPositive());
            assertThat(type.displayName()).doesNotContain("_");
            assertThat(MovementType.isInbound(type)).isEqualTo(type.isPositive());
        });

        assertThat(MovementType.ADJUSTMENT_IN.isReasonRequired()).isTrue();
        assertThat(MovementType.DAMAGED.isReasonRequired()).isTrue();
        assertThat(MovementType.PURCHASE.isReasonRequired()).isFalse();

        assertThatThrownBy(() -> MovementType.isInbound(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void requestsRoundTripFormStateAndRecordsPreserveFilterCriteria() {
        CategoryRequest c = new CategoryRequest();
        c.setName("C");
        c.setDescription("D");
        c.setActive(false);
        c.setVersion(1L);

        assertThat(c.getName()).isEqualTo("C");
        assertThat(c.getDescription()).isEqualTo("D");
        assertThat(c.isActive()).isFalse();
        assertThat(c.getVersion()).isEqualTo(1L);

        SupplierRequest s = new SupplierRequest();
        s.setName("S");
        s.setContactName("C");
        s.setEmail("e@x.com");
        s.setPhone("1");
        s.setActive(false);
        s.setVersion(2L);

        assertThat(s.getName()).isEqualTo("S");
        assertThat(s.getContactName()).isEqualTo("C");
        assertThat(s.getEmail()).isEqualTo("e@x.com");
        assertThat(s.getPhone()).isEqualTo("1");
        assertThat(s.isActive()).isFalse();
        assertThat(s.getVersion()).isEqualTo(2L);

        ProductRequest p = new ProductRequest();
        p.setSku("P");
        p.setName("N");
        p.setDescription("D");
        p.setUnitPrice(BigDecimal.ONE);
        p.setQuantityOnHand(2);
        p.setMinimumStock(1);
        p.setCategoryId(3L);
        p.setSupplierId(4L);
        p.setActive(false);
        p.setVersion(5L);

        assertThat(p.getSku()).isEqualTo("P");
        assertThat(p.getName()).isEqualTo("N");
        assertThat(p.getDescription()).isEqualTo("D");
        assertThat(p.getUnitPrice()).isOne();
        assertThat(p.getQuantityOnHand()).isEqualTo(2);
        assertThat(p.getMinimumStock()).isOne();
        assertThat(p.getCategoryId()).isEqualTo(3);
        assertThat(p.getSupplierId()).isEqualTo(4);
        assertThat(p.isActive()).isFalse();
        assertThat(p.getVersion()).isEqualTo(5);

        assertThat(new CategoryFilter("x", true))
                .extracting(CategoryFilter::text, CategoryFilter::active)
                .containsExactly("x", true);
        assertThat(new SupplierFilter("x", false))
                .extracting(SupplierFilter::text, SupplierFilter::active)
                .containsExactly("x", false);
        ProductFilter pf = new ProductFilter("x", 1L, 2L, true, true);

        assertThat(pf)
                .extracting(
                        ProductFilter::text,
                        ProductFilter::categoryId,
                        ProductFilter::supplierId,
                        ProductFilter::active,
                        ProductFilter::lowStockOnly)
                .containsExactly("x", 1L, 2L, true, true);

        StockMovementRequest m = new StockMovementRequest();
        m.setProductId(6L);
        m.setMovementType(MovementType.SALE);
        m.setQuantityDelta(-2);
        m.setReason("sale");

        assertThat(m.getProductId()).isEqualTo(6);
        assertThat(m.getMovementType()).isEqualTo(MovementType.SALE);
        assertThat(m.getQuantityDelta()).isEqualTo(-2);
        assertThat(m.getReason()).isEqualTo("sale");
    }

    @Test
    void exceptionTypesRetainActionableMessagesAndCause() {
        Throwable cause = new IllegalStateException("db");

        assertThat(new CategoryException("c")).hasMessage("c");
        assertThat(new SupplierException("s")).hasMessage("s");
        assertThat(new ProductException("p")).hasMessage("p");
        assertThat(new StockMovementException("m")).hasMessage("m");
        assertThat(new StockMovementException("m", cause)).hasCause(cause);
    }
}
