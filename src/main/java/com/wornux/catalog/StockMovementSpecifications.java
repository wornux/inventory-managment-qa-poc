package com.wornux.catalog;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class StockMovementSpecifications {

    private StockMovementSpecifications() {}

    static Specification<StockMovement> from(StockMovementFilter filter) {
        StockMovementFilter safeFilter =
                filter == null ? new StockMovementFilter(null, null, null, null, "") : filter;

        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            if (safeFilter.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("createdDate"), safeFilter.createdFrom()));
            }

            if (safeFilter.createdTo() != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdDate"), safeFilter.createdTo()));
            }

            if (safeFilter.productId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("product").get("id"), safeFilter.productId()));
            }

            if (safeFilter.movementType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("movementType"), safeFilter.movementType()));
            }

            String username = safeFilter.username() == null
                    ? ""
                    : safeFilter.username().trim().toLowerCase(Locale.ROOT);
            if (!username.isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("user").get("username")), username));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
