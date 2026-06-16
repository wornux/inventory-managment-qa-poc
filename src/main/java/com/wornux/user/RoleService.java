package com.wornux.user;

import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class RoleService {

    private static final String ADMINISTRATOR = "ROLE_SYSTEM_ADMINISTRATOR";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AppUserRepository appUserRepository;

    public RoleService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            AppUserRepository appUserRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<Role> search(RoleFilter filter) {
        requireRead();
        RoleFilter safeFilter = filter == null ? new RoleFilter("", null, null) : filter;
        return roleRepository.search(normalizeSearch(safeFilter.text()), safeFilter.systemRole(), safeFilter.active());
    }

    @Transactional(readOnly = true)
    public Role get(Long id) {
        requireRead();
        return roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new RoleException("Role was not found."));
    }

    @Transactional(readOnly = true)
    public List<Permission> assignablePermissions() {
        requireRead();
        return permissionRepository.findAssignablePermissions();
    }

    @Transactional(readOnly = true)
    public long userCount(Long roleId) {
        requireRead();
        return appUserRepository.countByRolesId(roleId);
    }

    @Transactional(readOnly = true)
    public long permissionCount(Long roleId) {
        requireRead();
        return get(roleId).getPermissions().size();
    }

    @Transactional
    public Role create(@Valid RoleRequest request) {
        requireManage();
        validateUniqueCode(request.getCode());
        Role role = new Role(
                normalizeCode(request.getCode()),
                normalizeName(request.getName()),
                trimToNull(request.getDescription()),
                false);
        role.update(role.getName(), role.getDescription(), request.isActive(), requireActivePermissions(request.getPermissionIds()));
        return roleRepository.save(role);
    }

    @Transactional
    public Role update(Long id, @Valid RoleRequest request) {
        requireManage();
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new RoleException("Role was not found."));
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
                requireActivePermissions(request.getPermissionIds()));
        return roleRepository.save(role);
    }

    @Transactional
    public void deactivate(Long id) {
        requireManage();
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> new RoleException("Role was not found."));
        validateCustomRole(role);
        role.deactivate();
        roleRepository.save(role);
    }

    public boolean canManageRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return hasAuthority(authentication, ADMINISTRATOR);
    }

    private void requireRead() {
        if (!canManageRoles()) {
            throw new AccessDeniedException("ROLE:READ permission is required.");
        }
    }

    private void requireManage() {
        if (!canManageRoles()) {
            throw new AccessDeniedException("ROLE:CREATE/UPDATE/DELETE/ASSIGN permission is required.");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
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

    private Set<Permission> requireActivePermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw new RoleException("At least one permission must be selected.");
        }
        List<Permission> permissions = permissionRepository.findAllById(permissionIds).stream()
                .filter(permission -> permission.isActive()
                        && permission.getResource().isActive()
                        && permission.getAction().isActive())
                .toList();
        if (permissions.size() != permissionIds.size()) {
            throw new RoleException("One or more permissions are no longer active and have been deselected.");
        }
        return new LinkedHashSet<>(permissions);
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
