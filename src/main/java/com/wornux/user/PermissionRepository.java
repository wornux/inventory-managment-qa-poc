package com.wornux.user;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

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
}
