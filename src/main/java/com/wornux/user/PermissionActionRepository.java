package com.wornux.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionActionRepository extends JpaRepository<PermissionAction, Long> {

    List<PermissionAction> findByActiveTrueOrderByCodeAsc();
}
