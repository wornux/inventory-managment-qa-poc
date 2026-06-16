package com.wornux.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    long countByCategoryId(Long categoryId);

    long countByCategoryIdAndActiveTrue(Long categoryId);

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

    @EntityGraph(attributePaths = {"category", "supplier"})
    @Query("""
            select p
            from Product p
            where (:text = '' or lower(p.sku) like lower(concat('%', :text, '%'))
                    or lower(p.name) like lower(concat('%', :text, '%')))
                and (:categoryId is null or p.category.id = :categoryId)
                and (:supplierId is null or p.supplier.id = :supplierId)
                and (:active is null or p.active = :active)
                and (:lowStock = false or p.quantityOnHand <= p.minimumStock)
            order by lower(p.sku)
            """)
    List<Product> search(
            @Param("text") String text,
            @Param("categoryId") Long categoryId,
            @Param("supplierId") Long supplierId,
            @Param("active") Boolean active,
            @Param("lowStock") boolean lowStock);
}
