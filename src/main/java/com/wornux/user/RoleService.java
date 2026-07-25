package com.wornux.user;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class RoleService {

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
        RoleFilter safeFilter = filter == null ? new RoleFilter("", null, null) : filter;

        return roleRepository.search(normalizeSearch(safeFilter.text()), safeFilter.systemRole(), safeFilter.active());
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

    @Transactional
    public Role create(@Valid RoleRequest request) {
        authorizationService.check(AppPermission.ROLE_CREATE);
        authorizationService.check(AppPermission.ROLE_ASSIGN);
        validateUniqueCode(request.getCode());
        Set<AppPermission> permissions = requireAssignablePermissions(request.getPermissions());
        Role role = new Role(
                normalizeCode(request.getCode()),
                normalizeName(request.getName()),
                trimToNull(request.getDescription()),
                false);
        role.update(role.getName(), role.getDescription(), request.isActive(), permissions);

        return roleRepository.save(role);
    }

    @Transactional
    public Role update(Long id, @Valid RoleRequest request) {
        authorizationService.check(AppPermission.ROLE_UPDATE);
        authorizationService.check(AppPermission.ROLE_ASSIGN);
        Role role = roleRepository.findById(id).orElseThrow(() -> new RoleException("Role was not found."));
        validateCustomRole(role);

        if (!Objects.equals(role.getVersion(), request.getVersion())) {
            throw new RoleException("Role was updated by another administrator. Refresh the form and try again.");
        }

        if (!role.getCode().equalsIgnoreCase(normalizeCode(request.getCode()))) {
            throw new RoleException("Role code cannot be changed.");
        }

        role.update(
                normalizeName(request.getName()),
                trimToNull(request.getDescription()),
                request.isActive(),
                requireAssignablePermissions(request.getPermissions()));

        return roleRepository.save(role);
    }

    @Transactional
    public void deactivate(Long id) {
        authorizationService.check(AppPermission.ROLE_DELETE);
        Role role = roleRepository.findById(id).orElseThrow(() -> new RoleException("Role was not found."));
        validateCustomRole(role);
        role.deactivate();
        roleRepository.save(role);
    }

    public boolean canCreateRoles() {
        return authorizationService.canAll(Set.of(AppPermission.ROLE_CREATE, AppPermission.ROLE_ASSIGN));
    }

    public boolean canUpdateRoles() {
        return authorizationService.canAll(Set.of(AppPermission.ROLE_UPDATE, AppPermission.ROLE_ASSIGN));
    }

    public boolean canDeleteRoles() {
        return authorizationService.can(AppPermission.ROLE_DELETE);
    }

    private void validateUniqueCode(String code) {
        if (roleRepository.existsByCodeIgnoreCase(normalizeCode(code))) {
            throw new RoleException("Role code already exists. Please choose a different one.");
        }
    }

    private void validateCustomRole(Role role) {
        if (role.isSystemRole()) {
            throw new RoleException("System roles cannot be edited.");
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
