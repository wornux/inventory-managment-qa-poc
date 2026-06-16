package com.wornux.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByActiveTrueOrderByNameAsc();

    @Query("""
            select supplier
            from Supplier supplier
            where (:text = '' or lower(supplier.name) like lower(concat('%', :text, '%'))
                    or lower(supplier.contactName) like lower(concat('%', :text, '%')))
                and (:active is null or supplier.active = :active)
            order by lower(supplier.name)
            """)
    List<Supplier> search(@Param("text") String text, @Param("active") Boolean active);
}
