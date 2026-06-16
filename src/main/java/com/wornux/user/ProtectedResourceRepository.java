package com.wornux.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtectedResourceRepository extends JpaRepository<ProtectedResource, Long> {

    List<ProtectedResource> findByActiveTrueOrderByCodeAsc();
}
