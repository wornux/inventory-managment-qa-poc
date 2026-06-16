package com.wornux.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    long countByRolesId(Long roleId);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByOidcIssuerAndOidcSubject(String oidcIssuer, String oidcSubject);

    @Query("""
            select count(permission) > 0
            from AppUser user
            join user.roles role
            join role.permissions permission
            join permission.resource resource
            join permission.action action
            where (lower(user.username) = lower(:principal) or lower(user.email) = lower(:principal))
                and user.active = true
                and role.active = true
                and permission.active = true
                and resource.active = true
                and action.active = true
                and resource.code = :resourceCode
                and action.code = :actionCode
            """)
    boolean hasActivePermission(
            @Param("principal") String principal,
            @Param("resourceCode") String resourceCode,
            @Param("actionCode") String actionCode);

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findWithRolesById(Long id);

    @EntityGraph(attributePaths = "roles")
    @Query("""
            select distinct user
            from AppUser user
            left join user.roles role
            where (:text = '' or lower(user.username) like lower(concat('%', :text, '%'))
                    or lower(user.email) like lower(concat('%', :text, '%')))
                and (:active is null or user.active = :active)
            order by user.username
            """)
    java.util.List<AppUser> search(@Param("text") String text, @Param("active") Boolean active);
}
