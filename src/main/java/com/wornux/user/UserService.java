package com.wornux.user;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class UserService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final AuthorizationService authorizationService;

    public UserService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            AuthorizationService authorizationService) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public Page<AppUser> search(UserFilter filter, Pageable pageable) {
        requireRead();

        return appUserRepository.findAll(toSpecification(filter), pageable);
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        requireRead();

        return appUserRepository.findWithRolesById(id).orElseThrow(() -> new UserException("User was not found."));
    }

    @Transactional(readOnly = true)
    public List<Role> activeRoles() {
        requireRead();

        return roleRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional
    public AppUser create(@Valid UserRequest request) {
        authorizationService.check(AppPermission.USER_CREATE);
        authorizationService.check(AppPermission.USER_ASSIGN);
        validateUniqueUsername(request.getUsername(), null);
        validateUniqueEmail(request.getEmail(), null);
        Set<Role> roles = requireActiveRoles(request.getRoleIds());

        AppUser user =
                new AppUser(normalizeUsername(request.getUsername()), normalizeEmail(request.getEmail()), null, null);
        user.setActive(request.isActive());
        roles.forEach(user::addRole);

        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser update(Long id, @Valid UserRequest request) {
        authorizationService.check(AppPermission.USER_UPDATE);
        authorizationService.check(AppPermission.USER_ASSIGN);
        AppUser user =
                appUserRepository.findWithRolesById(id).orElseThrow(() -> new UserException("User was not found."));

        if (!Objects.equals(user.getVersion(), request.getVersion())) {
            throw new UserException("User was updated by another administrator. Refresh the form and try again.");
        }

        validateUniqueUsername(request.getUsername(), id);
        validateUniqueEmail(request.getEmail(), id);
        user.update(
                normalizeUsername(request.getUsername()),
                normalizeEmail(request.getEmail()),
                request.isActive(),
                requireActiveRoles(request.getRoleIds()));

        return appUserRepository.save(user);
    }

    @Transactional
    public void deactivate(Long id) {
        authorizationService.check(AppPermission.USER_DELETE);
        AppUser user =
                appUserRepository.findWithRolesById(id).orElseThrow(() -> new UserException("User was not found."));

        if (isCurrentUser(user)) {
            throw new UserException("You cannot deactivate your own account.");
        }

        user.deactivate();
        appUserRepository.save(user);
    }

    public boolean canCreateUsers() {
        return authorizationService.canAll(Set.of(AppPermission.USER_CREATE, AppPermission.USER_ASSIGN));
    }

    public boolean canUpdateUsers() {
        return authorizationService.canAll(Set.of(AppPermission.USER_UPDATE, AppPermission.USER_ASSIGN));
    }

    public boolean canDeleteUsers() {
        return authorizationService.can(AppPermission.USER_DELETE);
    }

    private void requireRead() {
        authorizationService.check(AppPermission.USER_VIEW);
    }

    private void validateUniqueUsername(String username, Long id) {
        boolean exists = id == null
                ? appUserRepository.existsByUsernameIgnoreCase(normalizeUsername(username))
                : appUserRepository.existsByUsernameIgnoreCaseAndIdNot(normalizeUsername(username), id);

        if (exists) {
            throw new UserException("Username already exists. Please choose a different one.");
        }
    }

    private void validateUniqueEmail(String email, Long id) {
        boolean exists = id == null
                ? appUserRepository.existsByEmailIgnoreCase(normalizeEmail(email))
                : appUserRepository.existsByEmailIgnoreCaseAndIdNot(normalizeEmail(email), id);

        if (exists) {
            throw new UserException("Email already registered. Please use a different one.");
        }
    }

    private Set<Role> requireActiveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new UserException("At least one role must be selected.");
        }

        List<Role> roles = roleRepository.findAllById(roleIds).stream()
                .filter(Role::isActive)
                .toList();

        if (roles.size() != roleIds.size()) {
            throw new UserException("At least one role must be selected.");
        }

        Set<AppPermission> permissions =
                roles.stream().flatMap(role -> role.getPermissions().stream()).collect(Collectors.toSet());

        if (!authorizationService.canAll(permissions)) {
            throw new UserException("You cannot assign a role containing permissions that you do not have.");
        }

        return new LinkedHashSet<>(roles);
    }

    private boolean isCurrentUser(AppUser user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            return false;
        }

        String current = authentication.getName();

        return user.getUsername().equalsIgnoreCase(current) || user.getEmail().equalsIgnoreCase(current);
    }

    private Specification<AppUser> toSpecification(UserFilter filter) {
        UserFilter safeFilter = filter == null ? new UserFilter("", null) : filter;
        String text = normalizeSearch(safeFilter.text());

        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            if (!text.isEmpty()) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), "%" + text + "%"),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + text + "%")));
            }

            if (safeFilter.active() != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), safeFilter.active()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
