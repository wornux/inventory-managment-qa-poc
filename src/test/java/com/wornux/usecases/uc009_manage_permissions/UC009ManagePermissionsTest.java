package com.wornux.usecases.uc009_manage_permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.ui.views.PermissionsView;
import com.wornux.usecases.PostgresContainerConfig;
import com.wornux.user.Permission;
import com.wornux.user.PermissionAction;
import com.wornux.user.PermissionActionRepository;
import com.wornux.user.PermissionException;
import com.wornux.user.PermissionFilter;
import com.wornux.user.PermissionRepository;
import com.wornux.user.PermissionRequest;
import com.wornux.user.PermissionService;
import com.wornux.user.ProtectedResource;
import com.wornux.user.ProtectedResourceRepository;
import com.wornux.user.Role;
import com.wornux.user.RoleRepository;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(PostgresContainerConfig.class)
@Transactional
class UC009ManagePermissionsTest {

    private final PermissionService permissionService;
    private final PermissionRepository permissionRepository;
    private final ProtectedResourceRepository resourceRepository;
    private final PermissionActionRepository actionRepository;
    private final RoleRepository roleRepository;

    @Autowired
    UC009ManagePermissionsTest(
            PermissionService permissionService,
            PermissionRepository permissionRepository,
            ProtectedResourceRepository resourceRepository,
            PermissionActionRepository actionRepository,
            RoleRepository roleRepository) {
        this.permissionService = permissionService;
        this.permissionRepository = permissionRepository;
        this.resourceRepository = resourceRepository;
        this.actionRepository = actionRepository;
        this.roleRepository = roleRepository;
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void mainFlow_viewFilterCreateEditAndDeactivatePermissions() {
        MissingPair pair = missingPair();
        Permission created = permissionService.create(request(pair.resource(), pair.action(), "Temporary permission", true));

        assertThat(permissionService.search(new PermissionFilter(pair.resource().getId(), pair.action().getId(), true)))
                .extracting(Permission::getId)
                .contains(created.getId());

        PermissionRequest update = request(pair.resource(), pair.action(), "Updated permission", true);
        update.setVersion(created.getVersion());
        permissionService.update(created.getId(), update);

        Permission updated = permissionService.get(created.getId());
        assertThat(updated.getDescription()).isEqualTo("Updated permission");

        permissionService.deactivate(updated.getId());

        assertThat(permissionService.search(new PermissionFilter(pair.resource().getId(), pair.action().getId(), false)))
                .extracting(Permission::getId)
                .contains(updated.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af1_duplicateResourceActionPairIsRejected() {
        Permission existing = permissionRepository.findAssignablePermissions().getFirst();

        assertThatThrownBy(() -> permissionService.create(request(existing.getResource(), existing.getAction(), null, true)))
                .isInstanceOf(PermissionException.class)
                .hasMessage("This resource-action combination already exists.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af2_missingRequiredFieldsAreRejected() {
        var request = new PermissionRequest();

        assertThatThrownBy(() -> permissionService.create(request))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Resource is required.")
                .hasMessageContaining("Action is required.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af3_inactiveResourceOrActionIsRejectedOnCreate() {
        MissingPair pair = missingPair();
        pair.resource().deactivate();
        resourceRepository.saveAndFlush(pair.resource());

        assertThatThrownBy(() -> permissionService.create(request(pair.resource(), pair.action(), null, true)))
                .isInstanceOf(PermissionException.class)
                .hasMessage("The selected resource and/or action is inactive. Please select active resources and actions only.");
    }

    @Test
    @WithMockUser(username = "viewer", roles = "INVENTORY_VIEWER")
    void af4_insufficientPermissionsCannotManagePermissions() {
        MissingPair pair = missingPair();

        assertThat(permissionService.canManagePermissions()).isFalse();
        assertThatThrownBy(() -> permissionService.search(new PermissionFilter(null, null, true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("PERMISSION:READ permission is required.");
        assertThatThrownBy(() -> permissionService.create(request(pair.resource(), pair.action(), null, true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("PERMISSION:CREATE/UPDATE/DELETE permission is required.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af5_permissionWithRoleAssignmentsCanBeDeactivatedWithoutRemovingAssignments() {
        Permission assigned = permissionRepository.findAssignablePermissions().stream()
                .filter(permission -> permissionService.roleCount(permission.getId()) > 0)
                .findFirst()
                .orElseThrow();
        long roles = permissionService.roleCount(assigned.getId());

        permissionService.deactivate(assigned.getId());

        assertThat(permissionService.get(assigned.getId()).isActive()).isFalse();
        assertThat(permissionService.roleCount(assigned.getId())).isEqualTo(roles);
    }

    @Test
    void af6_sidebarFormDirtyStateIsOwnedByPermissionsView() throws NoSuchFieldException {
        assertThat(PermissionsView.class.getDeclaredField("dirty")).isNotNull();
        assertThat(PermissionsView.class.getDeclaredField("dirtyDialog")).isNotNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af7_concurrentEditConflictIsRejected() {
        MissingPair pair = missingPair();
        Permission permission = permissionService.create(request(pair.resource(), pair.action(), null, true));
        PermissionRequest stale = request(pair.resource(), pair.action(), "stale", true);
        stale.setVersion(permission.getVersion() + 1);

        assertThatThrownBy(() -> permissionService.update(permission.getId(), stale))
                .isInstanceOf(PermissionException.class)
                .hasMessage("Permission was updated by another administrator. Refresh the form and try again.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void af8_inactiveCurrentResourceOrActionIsRejectedOnEdit() {
        Permission permission = permissionRepository.findAssignablePermissions().getFirst();
        permission.getAction().deactivate();
        actionRepository.saveAndFlush(permission.getAction());
        PermissionRequest update = request(permission.getResource(), permission.getAction(), "blocked", true);
        update.setVersion(permission.getVersion());

        assertThatThrownBy(() -> permissionService.update(permission.getId(), update))
                .isInstanceOf(PermissionException.class)
                .hasMessage("The resource and/or action for this permission is no longer active.");
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br01_permissionResourceActionPairIsUnique() {
        af1_duplicateResourceActionPairIsRejected();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br02_bothResourceAndActionMustBeSelectedAndActive() {
        af2_missingRequiredFieldsAreRejected();
        af3_inactiveResourceOrActionIsRejectedOnCreate();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br03_descriptionIsOptional() {
        MissingPair pair = missingPair();
        Permission permission = permissionService.create(request(pair.resource(), pair.action(), null, true));

        assertThat(permission.getDescription()).isNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br04AndBr05_permissionsDeactivateAndKeepExistingRoleAssignments() {
        af5_permissionWithRoleAssignmentsCanBeDeactivatedWithoutRemovingAssignments();
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br06_deactivatedPermissionsCannotBeAssignedToNewRoles() {
        Permission permission = permissionRepository.findAssignablePermissions().getFirst();
        permissionService.deactivate(permission.getId());

        assertThat(permissionRepository.findAssignablePermissions()).extracting(Permission::getId)
                .doesNotContain(permission.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br07_dropdownOptionsShowOnlyActiveResourcesAndActions() {
        ProtectedResource resource = permissionService.activeResources().getFirst();
        PermissionAction action = permissionService.activeActions().getFirst();
        resource.deactivate();
        action.deactivate();
        resourceRepository.saveAndFlush(resource);
        actionRepository.saveAndFlush(action);

        assertThat(permissionService.activeResources()).extracting(ProtectedResource::getId).doesNotContain(resource.getId());
        assertThat(permissionService.activeActions()).extracting(PermissionAction::getId).doesNotContain(action.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br08_gridDisplaysRoleAssignmentCount() {
        Permission assigned = permissionRepository.findAssignablePermissions().stream()
                .filter(permission -> permissionService.roleCount(permission.getId()) > 0)
                .findFirst()
                .orElseThrow();
        long directCount = roleRepository.findAll().stream()
                .filter(role -> role.getPermissions().stream().anyMatch(permission -> permission.getId().equals(assigned.getId())))
                .count();

        assertThat(permissionService.roleCount(assigned.getId())).isEqualTo(directCount);
    }

    @Test
    @WithMockUser(username = "admin", roles = "SYSTEM_ADMINISTRATOR")
    void br09_resourceAndActionCannotBeEditedAfterCreation() {
        MissingPair pair = missingPair();
        Permission permission = permissionService.create(request(pair.resource(), pair.action(), null, true));
        MissingPair otherPair = missingPairExcluding(pair);
        PermissionRequest update = request(otherPair.resource(), otherPair.action(), null, true);
        update.setVersion(permission.getVersion());

        assertThatThrownBy(() -> permissionService.update(permission.getId(), update))
                .isInstanceOf(PermissionException.class)
                .hasMessage("Permission resource and action cannot be changed.");
    }

    private PermissionRequest request(ProtectedResource resource, PermissionAction action, String description, boolean active) {
        var request = new PermissionRequest();
        request.setResourceId(resource.getId());
        request.setActionId(action.getId());
        request.setDescription(description);
        request.setActive(active);
        return request;
    }

    private MissingPair missingPair() {
        List<ProtectedResource> resources = resourceRepository.findByActiveTrueOrderByCodeAsc();
        List<PermissionAction> actions = actionRepository.findByActiveTrueOrderByCodeAsc();
        return resources.stream()
                .flatMap(resource -> actions.stream().map(action -> new MissingPair(resource, action)))
                .filter(pair -> !permissionRepository.existsByResourceIdAndActionId(pair.resource().getId(), pair.action().getId()))
                .findFirst()
                .orElseThrow();
    }

    private MissingPair missingPairExcluding(MissingPair excluded) {
        List<ProtectedResource> resources = resourceRepository.findByActiveTrueOrderByCodeAsc();
        List<PermissionAction> actions = actionRepository.findByActiveTrueOrderByCodeAsc();
        return resources.stream()
                .flatMap(resource -> actions.stream().map(action -> new MissingPair(resource, action)))
                .filter(pair -> !pair.equals(excluded))
                .filter(pair -> !permissionRepository.existsByResourceIdAndActionId(pair.resource().getId(), pair.action().getId()))
                .findFirst()
                .orElseThrow();
    }

    private record MissingPair(ProtectedResource resource, PermissionAction action) {
    }
}
