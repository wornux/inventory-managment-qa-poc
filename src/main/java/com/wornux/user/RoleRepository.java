package com.wornux.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    Optional<Role> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Role> findByActiveTrueOrderByNameAsc();
}
