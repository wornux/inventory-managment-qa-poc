package com.wornux.user;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class PermissionService {

    private static final String ADMINISTRATOR = "ROLE_SYSTEM_ADMINISTRATOR";

    private final PermissionRepository permissionRepository;
    private final ProtectedResourceRepository resourceRepository;
    private final PermissionActionRepository actionRepository;
    private final RoleRepository roleRepository;

    public PermissionService(
            PermissionRepository permissionRepository,
            ProtectedResourceRepository resourceRepository,
            PermissionActionRepository actionRepository,
            RoleRepository roleRepository) {
        this.permissionRepository = permissionRepository;
        this.resourceRepository = resourceRepository;
        this.actionRepository = actionRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<Permission> search(PermissionFilter filter) {
        requireRead();
        PermissionFilter safeFilter = filter == null ? new PermissionFilter(null, null, null) : filter;
        return permissionRepository.search(safeFilter.resourceId(), safeFilter.actionId(), safeFilter.active());
    }

    @Transactional(readOnly = true)
    public Permission get(Long id) {
        requireRead();
        return permissionRepository.findWithResourceAndActionById(id)
                .orElseThrow(() -> new PermissionException("Permission was not found."));
    }

    @Transactional(readOnly = true)
    public List<ProtectedResource> activeResources() {
        requireRead();
        return resourceRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<PermissionAction> activeActions() {
        requireRead();
        return actionRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public long roleCount(Long permissionId) {
        requireRead();
        return roleRepository.countByPermissionsId(permissionId);
    }

    @Transactional
    public Permission create(@Valid PermissionRequest request) {
        requireManage();
        ProtectedResource resource = requireActiveResource(request.getResourceId());
        PermissionAction action = requireActiveAction(request.getActionId());
        if (permissionRepository.existsByResourceIdAndActionId(resource.getId(), action.getId())) {
            throw new PermissionException("This resource-action combination already exists.");
        }
        return permissionRepository.save(new Permission(resource, action, trimToNull(request.getDescription()), request.isActive()));
    }

    @Transactional
    public Permission update(Long id, @Valid PermissionRequest request) {
        requireManage();
        Permission permission = permissionRepository.findWithResourceAndActionById(id)
                .orElseThrow(() -> new PermissionException("Permission was not found."));
        if (!Objects.equals(permission.getVersion(), request.getVersion())) {
            throw new PermissionException("Permission was updated by another administrator. Refresh the form and try again.");
        }
        if (!Objects.equals(permission.getResource().getId(), request.getResourceId())
                || !Objects.equals(permission.getAction().getId(), request.getActionId())) {
            throw new PermissionException("Permission resource and action cannot be changed.");
        }
        validateCurrentResourceAndAction(permission);
        permission.update(trimToNull(request.getDescription()), request.isActive());
        return permissionRepository.save(permission);
    }

    @Transactional
    public void deactivate(Long id) {
        requireManage();
        Permission permission = permissionRepository.findWithResourceAndActionById(id)
                .orElseThrow(() -> new PermissionException("Permission was not found."));
        permission.deactivate();
        permissionRepository.save(permission);
    }

    public boolean canManagePermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> ADMINISTRATOR.equals(grantedAuthority.getAuthority()));
    }

    private void requireRead() {
        if (!canManagePermissions()) {
            throw new AccessDeniedException("PERMISSION:READ permission is required.");
        }
    }

    private void requireManage() {
        if (!canManagePermissions()) {
            throw new AccessDeniedException("PERMISSION:CREATE/UPDATE/DELETE permission is required.");
        }
    }

    private ProtectedResource requireActiveResource(Long resourceId) {
        ProtectedResource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new PermissionException("The selected resource and/or action is inactive. Please select active resources and actions only."));
        if (!resource.isActive()) {
            throw new PermissionException("The selected resource and/or action is inactive. Please select active resources and actions only.");
        }
        return resource;
    }

    private PermissionAction requireActiveAction(Long actionId) {
        PermissionAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new PermissionException("The selected resource and/or action is inactive. Please select active resources and actions only."));
        if (!action.isActive()) {
            throw new PermissionException("The selected resource and/or action is inactive. Please select active resources and actions only.");
        }
        return action;
    }

    private void validateCurrentResourceAndAction(Permission permission) {
        if (!permission.getResource().isActive() || !permission.getAction().isActive()) {
            throw new PermissionException("The resource and/or action for this permission is no longer active.");
        }
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
