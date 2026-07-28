package com.wornux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.data.jpa.domain.Specification;

public final class SpecificationTestSupport {

    private SpecificationTestSupport() {}

    @SuppressWarnings("unchecked")
    public static <T> int predicateCount(Specification<T> specification) {
        Root<T> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
        AtomicInteger count = new AtomicInteger(-1);
        when(criteriaBuilder.and(any(Predicate[].class))).thenAnswer(invocation -> {
            count.set(invocation.getArguments().length);

            return mock(Predicate.class);
        });

        specification.toPredicate(root, query, criteriaBuilder);

        return count.get();
    }
}
