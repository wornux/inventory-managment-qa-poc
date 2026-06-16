package com.wornux.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<Category> findByNameIgnoreCase(String name);

    List<Category> findByActiveTrueOrderByNameAsc();

    @Query("""
            select category
            from Category category
            where (:text = '' or lower(category.name) like lower(concat('%', :text, '%')))
                and (:active is null or category.active = :active)
            order by lower(category.name)
            """)
    List<Category> search(@Param("text") String text, @Param("active") Boolean active);
}
