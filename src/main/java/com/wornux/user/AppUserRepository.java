package com.wornux.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    long countByRolesId(Long roleId);

    List<AppUser> findDistinctByRolesIdOrderByUsernameAsc(Long roleId);

    @Query("""
            select role.id, count(user.id)
            from AppUser user join user.roles role
            where role.id in :roleIds
            group by role.id
            """)
    List<Object[]> countMembersByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByOidcIssuerAndOidcSubject(String oidcIssuer, String oidcSubject);

    @EntityGraph(attributePaths = "roles")
    @Query("""
            select user from AppUser user
            where lower(user.username) = lower(:principal) or lower(user.email) = lower(:principal)
            """)
    Optional<AppUser> findForAuthorization(@Param("principal") String principal);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findWithRolesById(Long id);
}
