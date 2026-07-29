package com.wornux.catalog;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

    boolean existsByProductId(Long productId);

    @Override
    @EntityGraph(attributePaths = {"product", "user"})
    List<StockMovement> findAll(Specification<StockMovement> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = {"product", "user"})
    Page<StockMovement> findAll(Specification<StockMovement> specification, Pageable pageable);

    @Query("""
            select distinct movement.user.username
            from StockMovement movement
            where movement.user is not null
            order by movement.user.username
            """)
    List<String> findDistinctUsernames();

    @Query("""
            select
                movement.product.sku as sku,
                movement.product.name as name,
                sum(abs(movement.quantityDelta)) as unitsSold
            from StockMovement movement
            where movement.movementType = com.wornux.catalog.MovementType.SALE
                and movement.product.active = true
            group by movement.product.id, movement.product.sku, movement.product.name
            order by sum(abs(movement.quantityDelta)) desc, movement.product.id asc
            """)
    List<TopSellingProductView> findTopSellingProducts(Pageable pageable);

    @EntityGraph(attributePaths = {"product", "user"})
    List<StockMovement> findTop6ByOrderByCreatedDateDescIdDesc();

    @Query(value = """
                    with activity_dates as (
                        select distinct created_date::date as activity_date
                        from stock_movement
                        order by activity_date desc
                        limit 7
                    )
                    select
                        movement.created_date::date as "activityDate",
                        coalesce(sum(movement.quantity_delta)
                            filter (where movement.quantity_delta > 0), 0) as "inboundUnits",
                        coalesce(sum(abs(movement.quantity_delta))
                            filter (where movement.quantity_delta < 0), 0) as "outboundUnits"
                    from stock_movement movement
                    join activity_dates on movement.created_date::date = activity_dates.activity_date
                    group by movement.created_date::date
                    order by movement.created_date::date
                    """, nativeQuery = true)
    List<MovementVolumeView> findLatestMovementVolume();

    interface TopSellingProductView {
        String getSku();

        String getName();

        long getUnitsSold();
    }

    interface MovementVolumeView {
        LocalDate getActivityDate();

        long getInboundUnits();

        long getOutboundUnits();
    }
}
