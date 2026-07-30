package com.wornux.user;

import com.wornux.security.KeycloakAdminBootstrapProperties;
import com.wornux.security.KeycloakAdminClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final AuthorizationService authorizationService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakAdminBootstrapProperties keycloakProperties;

    public UserService(
            AppUserRepository appUserRepository,
            RoleRepository roleRepository,
            AuthorizationService authorizationService,
            KeycloakAdminClient keycloakAdminClient,
            KeycloakAdminBootstrapProperties keycloakProperties) {
        this.appUserRepository = appUserRepository;
        this.roleRepository = roleRepository;
        this.authorizationService = authorizationService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.keycloakProperties = keycloakProperties;
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
        authorizationService.check(AppPermission.ROLE_ASSIGN);
        validateUniqueUsername(request.getUsername(), null);
        validateUniqueEmail(request.getEmail(), null);
        Set<Role> roles = requireActiveRoles(request.getRoleIds());
        String username = normalizeUsername(request.getUsername());
        String email = normalizeEmail(request.getEmail());
        String password = requirePassword(request.getPassword());
        KeycloakAdminClient.KeycloakUser identity = createIdentity(username, email, password);

        // ponytail: Keycloak and PostgreSQL are not atomic; add reconciliation if provisioning volume grows.
        AppUser user = new AppUser(username, email, keycloakProperties.issuer(), identity.id());
        user.setActive(request.isActive());
        roles.forEach(user::addRole);

        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser update(Long id, @Valid UserRequest request) {
        authorizationService.check(AppPermission.USER_UPDATE);
        AppUser user =
                appUserRepository.findWithRolesById(id).orElseThrow(() -> new UserException("User was not found."));

        if (!Objects.equals(user.getVersion(), request.getVersion())) {
            throw new UserException("User was updated by another administrator. Refresh the form and try again.");
        }

        Set<Role> roles = rolesForUpdate(user, request.getRoleIds());
        String username = normalizeUsername(request.getUsername());
        String email = normalizeEmail(request.getEmail());

        if (!user.getUsername().equals(username) || !user.getEmail().equals(email)) {
            throw new UserException("Username and email are managed by Keycloak and cannot be changed here.");
        }

        user.update(username, email, request.isActive(), roles);
        AppUser saved = appUserRepository.save(user);
        authorizationService.invalidateUser(id);

        return saved;
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
        authorizationService.invalidateUser(id);
    }

    public boolean canCreateUsers() {
        return authorizationService.canAll(Set.of(AppPermission.USER_CREATE, AppPermission.ROLE_ASSIGN));
    }

    public boolean canUpdateUsers() {
        return authorizationService.can(AppPermission.USER_UPDATE);
    }

    public boolean canAssignRoles() {
        return authorizationService.can(AppPermission.ROLE_ASSIGN);
    }

    public boolean canDeleteUsers() {
        return authorizationService.can(AppPermission.USER_DELETE);
    }

    private void requireRead() {
        authorizationService.check(AppPermission.USER_VIEW);
    }

    private KeycloakAdminClient.KeycloakUser createIdentity(String username, String email, String password) {
        try {
            return keycloakAdminClient.createUser(keycloakProperties, username, email, password);
        } catch (RuntimeException exception) {
            LOGGER.error("event=\"keycloak.user.create.failed\"", exception);
            throw new UserException("The Keycloak account could not be created.");
        }
    }

    private String requirePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8) {
            throw new UserException("Password must be at least 8 characters.");
        }

        return password;
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

    private Set<Role> rolesForUpdate(AppUser user, Set<Long> requestedRoleIds) {
        Set<Long> currentRoleIds = user.getRoles().stream().map(Role::getId).collect(Collectors.toSet());

        if (currentRoleIds.equals(requestedRoleIds)) {
            return new LinkedHashSet<>(user.getRoles());
        }

        authorizationService.check(AppPermission.ROLE_ASSIGN);
        Set<Role> requestedRoles = requireActiveRoles(requestedRoleIds);
        Set<Role> removedRoles = user.getRoles().stream()
                .filter(role -> !requestedRoleIds.contains(role.getId()))
                .collect(Collectors.toSet());
        requireManageableRoles(removedRoles);

        return requestedRoles;
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

        requireManageableRoles(roles);

        return new LinkedHashSet<>(roles);
    }

    private void requireManageableRoles(Iterable<Role> roles) {
        var permissions = new LinkedHashSet<AppPermission>();

        for (Role role : roles) {
            if (!authorizationService.canManagePriority(role.getPriority())) {
                throw new UserException("You cannot assign or remove a role above your priority.");
            }

            permissions.addAll(role.getPermissions());
        }

        if (!permissions.isEmpty() && !authorizationService.canAll(permissions)) {
            throw new UserException("You cannot assign a role containing permissions that you do not have.");
        }
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
