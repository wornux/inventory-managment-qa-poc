package com.wornux.user;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class RoleService {

    private static final Sort ROLE_ORDER = Sort.by(Sort.Order.desc("priority"), Sort.Order.asc("code"));
    private final RoleRepository roleRepository;
    private final AppUserRepository appUserRepository;
    private final AuthorizationService authorizationService;

    public RoleService(
            RoleRepository roleRepository,
            AppUserRepository appUserRepository,
            AuthorizationService authorizationService) {
        this.roleRepository = roleRepository;
        this.appUserRepository = appUserRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<Role> search(RoleFilter filter) {
        authorizationService.check(AppPermission.ROLE_VIEW);

        return roleRepository.findAll(toSpecification(filter), ROLE_ORDER);
    }

    @Transactional(readOnly = true)
    public Role get(Long id) {
        authorizationService.check(AppPermission.ROLE_VIEW);

        return roleRepository.findById(id).orElseThrow(() -> new RoleException("Role was not found."));
    }

    @Transactional(readOnly = true)
    public List<AppPermission> assignablePermissions() {
        authorizationService.check(AppPermission.ROLE_VIEW);

        return Arrays.asList(AppPermission.values());
    }

    @Transactional(readOnly = true)
    public long userCount(Long roleId) {
        authorizationService.check(AppPermission.ROLE_VIEW);

        return appUserRepository.countByRolesId(roleId);
    }

    @Transactional(readOnly = true)
    public long permissionCount(Long roleId) {
        return get(roleId).getPermissions().size();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> userCounts(Collection<Long> roleIds) {
        authorizationService.check(AppPermission.ROLE_VIEW);

        if (roleIds.isEmpty()) {
            return Map.of();
        }

        return appUserRepository.countMembersByRoleIds(roleIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    @Transactional(readOnly = true)
    public List<AppUser> members(Long roleId) {
        authorizationService.check(AppPermission.ROLE_VIEW);

        return appUserRepository.findDistinctByRolesIdOrderByUsernameAsc(roleId);
    }

    @Transactional(readOnly = true)
    public List<AppUser> assignmentCandidates(Long roleId) {
        authorizationService.check(AppPermission.ROLE_ASSIGN);
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleException("Role was not found."));
        requireManageableRole(role);

        return appUserRepository.findAll(Sort.by("username")).stream()
                .filter(user ->
                        user.getRoles().stream().noneMatch(assigned -> Objects.equals(assigned.getId(), roleId)))
                .toList();
    }

    @Transactional
    public void assignMember(Long roleId, Long userId) {
        authorizationService.check(AppPermission.ROLE_ASSIGN);
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleException("Role was not found."));

        if (!role.isActive()) {
            throw new RoleException("Inactive roles cannot receive new members.");
        }

        requireManageableRole(role);
        AppUser user = requireUser(userId);
        user.addRole(role);
        appUserRepository.save(user);
        authorizationService.invalidateUser(userId);
    }

    @Transactional
    public void removeMember(Long roleId, Long userId) {
        authorizationService.check(AppPermission.ROLE_ASSIGN);
        Role role = roleRepository.findById(roleId).orElseThrow(() -> new RoleException("Role was not found."));
        requireManageableRole(role);
        AppUser user = requireUser(userId);
        user.getRoles().removeIf(assignedRole -> Objects.equals(assignedRole.getId(), roleId));
        appUserRepository.save(user);
        authorizationService.invalidateUser(userId);
    }

    @Transactional
    public Role create(@Valid RoleRequest request) {
        authorizationService.check(AppPermission.ROLE_CREATE);
        validateUniqueCode(request.getCode());
        Set<AppPermission> permissions = requireAssignablePermissions(request.getPermissions());
        int priority = requirePriority(request.getPriority());
        requireOutranksPriority(priority);
        Role role = new Role(
                normalizeCode(request.getCode()),
                normalizeName(request.getName()),
                trimToNull(request.getDescription()));
        role.update(role.getName(), role.getDescription(), priority, request.isActive(), permissions);

        return roleRepository.save(role);
    }

    @Transactional
    public Role update(Long id, @Valid RoleRequest request) {
        authorizationService.check(AppPermission.ROLE_UPDATE);
        Role role = roleRepository.findById(id).orElseThrow(() -> new RoleException("Role was not found."));

        if (!Objects.equals(role.getVersion(), request.getVersion())) {
            throw new RoleException("Role was updated by another administrator. Refresh the form and try again.");
        }

        if (!role.getCode().equalsIgnoreCase(normalizeCode(request.getCode()))) {
            throw new RoleException("Role code cannot be changed.");
        }

        requireManageableRole(role);
        int requestedPriority = requirePriority(request.getPriority());
        validatePriorityChange(role, requestedPriority);
        validateActiveChange(role, request.isActive());
        role.update(
                normalizeName(request.getName()),
                trimToNull(request.getDescription()),
                requestedPriority,
                request.isActive(),
                requireAssignablePermissions(request.getPermissions()));
        Role saved = roleRepository.save(role);
        authorizationService.invalidateAll();

        return saved;
    }

    @Transactional
    public void deactivate(Long id) {
        authorizationService.check(AppPermission.ROLE_DELETE);
        Role role = roleRepository.findById(id).orElseThrow(() -> new RoleException("Role was not found."));
        requireManageableRole(role);
        requireOutranksPriority(role.getPriority());
        role.deactivate();
        roleRepository.save(role);
        authorizationService.invalidateAll();
    }

    public boolean canCreateRoles() {
        return authorizationService.can(AppPermission.ROLE_CREATE);
    }

    public boolean canUpdateRoles() {
        return authorizationService.can(AppPermission.ROLE_UPDATE);
    }

    public boolean canUpdateRole(Role role) {
        return canUpdateRoles() && canManageRole(role);
    }

    public boolean canChangeActiveState(Role role) {
        return canUpdateRole(role) && authorizationService.outranksPriority(role.getPriority());
    }

    public boolean canDeleteRoles() {
        return authorizationService.can(AppPermission.ROLE_DELETE);
    }

    public boolean canDeactivateRole(Role role) {
        return canDeleteRoles() && canManageRole(role) && authorizationService.outranksPriority(role.getPriority());
    }

    public boolean canAssignRoles() {
        return authorizationService.can(AppPermission.ROLE_ASSIGN);
    }

    public boolean canAssignRole(Role role) {
        return canAssignRoles() && canManageRole(role);
    }

    private AppUser requireUser(Long id) {
        return appUserRepository.findWithRolesById(id).orElseThrow(() -> new UserException("User was not found."));
    }

    private void requireManageableRole(Role role) {
        if (!authorizationService.canManagePriority(role.getPriority())) {
            throw new RoleException("You cannot manage a role above your priority.");
        }

        if (!authorizationService.canAll(role.getPermissions())) {
            throw new RoleException("You cannot manage a role containing permissions that you do not have.");
        }
    }

    private boolean canManageRole(Role role) {
        return authorizationService.canManagePriority(role.getPriority())
                && authorizationService.canAll(role.getPermissions());
    }

    private void requireOutranksPriority(int priority) {
        if (!authorizationService.outranksPriority(priority)) {
            throw new RoleException("Role priority must be lower than your priority.");
        }
    }

    private int requirePriority(Integer priority) {
        if (priority == null || priority < 0 || priority > 100) {
            throw new RoleException("Priority must be between 0 and 100.");
        }

        return priority;
    }

    private void validatePriorityChange(Role role, int requestedPriority) {
        if (role.getPriority() == 100 && requestedPriority != 100) {
            throw new RoleException("The priority 100 role cannot change priority.");
        }

        if (requestedPriority != role.getPriority()) {
            requireOutranksPriority(requestedPriority);
        }
    }

    private void validateActiveChange(Role role, boolean requestedActive) {
        if (requestedActive != role.isActive() && !authorizationService.outranksPriority(role.getPriority())) {
            throw new RoleException("You cannot change the active state of a role at your priority.");
        }
    }

    private void validateUniqueCode(String code) {
        if (roleRepository.existsByCodeIgnoreCase(normalizeCode(code))) {
            throw new RoleException("Role code already exists. Please choose a different one.");
        }
    }

    private Set<AppPermission> requireAssignablePermissions(Set<AppPermission> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new RoleException("At least one permission must be selected.");
        }

        if (!authorizationService.canAll(requested)) {
            throw new RoleException("You cannot assign permissions that you do not have.");
        }

        return new LinkedHashSet<>(requested);
    }

    private Specification<Role> toSpecification(RoleFilter filter) {
        RoleFilter safeFilter = filter == null ? new RoleFilter("", null) : filter;
        String text = normalizeSearch(safeFilter.text());

        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            if (!text.isEmpty()) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), "%" + text + "%"),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + text + "%")));
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

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? "" : value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}
