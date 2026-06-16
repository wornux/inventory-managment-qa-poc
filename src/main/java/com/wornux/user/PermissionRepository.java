package com.wornux.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    boolean existsByResourceIdAndActionId(Long resourceId, Long actionId);

    @EntityGraph(attributePaths = {"resource", "action"})
    Optional<Permission> findWithResourceAndActionById(Long id);

    @EntityGraph(attributePaths = {"resource", "action"})
    @Query("""
            select permission
            from Permission permission
            join permission.resource resource
            join permission.action action
            where permission.active = true
                and resource.active = true
                and action.active = true
            order by resource.code, action.code
            """)
    List<Permission> findAssignablePermissions();

    @EntityGraph(attributePaths = {"resource", "action"})
    @Query("""
            select permission
            from Permission permission
            join permission.resource resource
            join permission.action action
            where (:resourceId is null or resource.id = :resourceId)
                and (:actionId is null or action.id = :actionId)
                and (:active is null or permission.active = :active)
            order by resource.code, action.code
            """)
    List<Permission> search(
            @Param("resourceId") Long resourceId,
            @Param("actionId") Long actionId,
            @Param("active") Boolean active);
}
