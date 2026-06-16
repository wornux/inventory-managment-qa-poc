package com.wornux.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Role> findByActiveTrueOrderByNameAsc();

    long countByPermissionsId(Long permissionId);

    @EntityGraph(attributePaths = {"permissions", "permissions.resource", "permissions.action"})
    Optional<Role> findWithPermissionsById(Long id);

    @EntityGraph(attributePaths = {"permissions", "permissions.resource", "permissions.action"})
    @Query("""
            select distinct role
            from Role role
            left join role.permissions permission
            where (:text = '' or lower(role.code) like lower(concat('%', :text, '%'))
                    or lower(role.name) like lower(concat('%', :text, '%')))
                and (:systemRole is null or role.systemRole = :systemRole)
                and (:active is null or role.active = :active)
            order by role.code
            """)
    List<Role> search(
            @Param("text") String text,
            @Param("systemRole") Boolean systemRole,
            @Param("active") Boolean active);
}
