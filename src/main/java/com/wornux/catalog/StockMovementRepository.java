package com.wornux.catalog;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    boolean existsByProductId(Long productId);

    @EntityGraph(attributePaths = {"product", "user"})
    @Query("""
            select movement
            from StockMovement movement
            where movement.createdDate >= :createdFrom
                and movement.createdDate < :createdTo
                and (:productId is null or movement.product.id = :productId)
                and (:movementType is null or movement.movementType = :movementType)
                and (:username = '' or (movement.user is not null and lower(movement.user.username) = lower(:username)))
            order by movement.createdDate desc, movement.id desc
            """)
    List<StockMovement> search(
            @Param("createdFrom") Instant createdFrom,
            @Param("createdTo") Instant createdTo,
            @Param("productId") Long productId,
            @Param("movementType") MovementType movementType,
            @Param("username") String username);

    @Query("""
            select distinct movement.user.username
            from StockMovement movement
            where movement.user is not null
            order by movement.user.username
            """)
    List<String> findDistinctUsernames();
}
