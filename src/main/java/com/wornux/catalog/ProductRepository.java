package com.wornux.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    long countByCategoryId(Long categoryId);

    long countByCategoryIdAndActiveTrue(Long categoryId);

    long countBySupplierId(Long supplierId);

    long countBySupplierIdAndActiveTrue(Long supplierId);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    @Query("""
            select count(p) > 0
            from Product p
            where lower(p.name) = lower(:name)
                and p.active = true
                and (:id is null or p.id <> :id)
            """)
    boolean existsActiveNameExcludingId(@Param("name") String name, @Param("id") Long id);

    @EntityGraph(attributePaths = {"category", "supplier"})
    Optional<Product> findWithCategoryAndSupplierById(Long id);

    @EntityGraph(attributePaths = {"category", "supplier"})
    List<Product> findByActiveTrueOrderBySkuAsc();

    @Query("""
            select count(product)
            from Product product
            where product.active = true
                and product.quantityOnHand <= product.minimumStock
            """)
    long countActiveLowStockProducts();

    @Query(value = """
                    select
                        count(*) as "activeProducts",
                        coalesce(sum(quantity_on_hand), 0) as "inventoryUnits",
                        coalesce(sum(unit_price * quantity_on_hand), 0) as "inventoryValue",
                        count(*) filter (where quantity_on_hand <= minimum_stock) as "lowStockProducts"
                    from product
                    where active
                    """, nativeQuery = true)
    InventorySummaryView summarizeActiveInventory();

    @Query("""
            select product
            from Product product
            where product.active = true
                and product.quantityOnHand <= product.minimumStock
            order by product.quantityOnHand asc, product.sku asc
            """)
    List<Product> findActiveLowStockProducts(Pageable pageable);

    interface InventorySummaryView {
        long getActiveProducts();

        long getInventoryUnits();

        BigDecimal getInventoryValue();

        long getLowStockProducts();
    }

    @Override
    @EntityGraph(attributePaths = {"category", "supplier"})
    Page<Product> findAll(@NonNull Specification<Product> specification, @NonNull Pageable pageable);
}
