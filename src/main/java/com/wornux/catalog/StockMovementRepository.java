package com.wornux.catalog;

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
}
