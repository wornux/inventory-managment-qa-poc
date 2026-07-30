package com.wornux.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wornux.SpecificationTestSupport;
import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AuthorizationService authorizationService;

    private RoleService roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, appUserRepository, new AuthorizationService(appUserRepository));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_persistsCustomRoleWithTypedPermissions() {
        authenticate("role:create", "product:update");
        RoleRequest request = request(AppPermission.PRODUCT_VIEW, AppPermission.PRODUCT_UPDATE);
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Role role = roleService.create(request);

        assertThat(role.getCode()).isEqualTo("CATALOG_EDITOR");
        assertThat(role.getPriority()).isEqualTo(10);
        assertThat(role.getPermissions()).containsExactly(AppPermission.PRODUCT_VIEW, AppPermission.PRODUCT_UPDATE);
    }

    @Test
    void members_returnsUsersAssignedToRole() {
        authenticate("role:view");
        AppUser member = new AppUser("editor", "editor@example.com", "issuer", "subject");
        when(appUserRepository.findDistinctByRolesIdOrderByUsernameAsc(7L)).thenReturn(List.of(member));

        assertThat(roleService.members(7L)).containsExactly(member);
    }

    @Test
    void assignmentCandidatesRequireRoleAssignmentAndExcludeExistingMembers() throws Exception {
        Role assignedRole = UserDomainTest.role("ASSIGNED", true, AppPermission.PRODUCT_VIEW);
        set(assignedRole, "id", 7L);
        AppUser assigned = new AppUser("assigned", "assigned@example.com", null, null);
        assigned.addRole(assignedRole);
        AppUser candidate = new AppUser("candidate", "candidate@example.com", null, null);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(assignedRole));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(appUserRepository.findAll(Sort.by("username"))).thenReturn(List.of(assigned, candidate));

        assertThat(directService().assignmentCandidates(7L)).containsExactly(candidate);
        verify(authorizationService).check(AppPermission.ROLE_ASSIGN);
    }

    @Test
    void assignmentCandidatesRejectMissingRoles() {
        when(roleRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directService().assignmentCandidates(7L))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role was not found.");
    }

    @Test
    void membershipChangesUseRoleAssignmentAndPermitRemovingTheLastRole() throws Exception {
        Role role = UserDomainTest.role("ASSIGNED", true, AppPermission.PRODUCT_VIEW);
        Role other = UserDomainTest.role("OTHER", true, AppPermission.PRODUCT_VIEW);
        set(role, "id", 7L);
        set(other, "id", 8L);
        AppUser user = new AppUser("member", "member@example.com", null, null);
        user.addRole(other);
        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));
        when(roleRepository.findById(8L)).thenReturn(Optional.of(other));
        when(appUserRepository.findWithRolesById(9L)).thenReturn(Optional.of(user));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);

        directService().assignMember(7L, 9L);

        assertThat(user.getRoles()).containsExactly(other, role);

        directService().removeMember(7L, 9L);

        assertThat(user.getRoles()).containsExactly(other);

        directService().removeMember(8L, 9L);

        assertThat(user.getRoles()).isEmpty();
        verify(authorizationService, times(3)).check(AppPermission.ROLE_ASSIGN);
        verify(appUserRepository, times(3)).save(user);
        verify(authorizationService, times(3)).invalidateUser(9L);
    }

    @Test
    void membershipChangesRejectMissingInactiveUnmanageableRolesAndMissingUsers() {
        Role inactive = UserDomainTest.role("INACTIVE", false, AppPermission.PRODUCT_VIEW);
        when(roleRepository.findById(1L)).thenReturn(Optional.empty());
        when(roleRepository.findById(2L)).thenReturn(Optional.of(inactive));
        when(roleRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directService().assignMember(1L, 9L))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role was not found.");
        assertThatThrownBy(() -> directService().assignMember(2L, 9L))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("Inactive roles");
        assertThatThrownBy(() -> directService().removeMember(4L, 9L))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role was not found.");

        Role elevated = UserDomainTest.role("ELEVATED", true, AppPermission.PRODUCT_DELETE);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(elevated));

        assertThatThrownBy(() -> directService().removeMember(3L, 9L))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("above your priority");

        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_DELETE))).thenReturn(false);

        assertThatThrownBy(() -> directService().removeMember(3L, 9L))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("permissions that you do not have");

        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_DELETE))).thenReturn(true);
        when(appUserRepository.findWithRolesById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directService().removeMember(3L, 9L))
                .isInstanceOf(UserException.class)
                .hasMessage("User was not found.");
    }

    @Test
    void userCounts_returnsCountsForRolesWithMembers() {
        authenticate("role:view");
        when(appUserRepository.countMembersByRoleIds(List.of(7L, 8L)))
                .thenReturn(List.<Object[]>of(new Object[] {7L, 3L}));

        assertThat(roleService.userCounts(List.of(7L, 8L)))
                .containsEntry(7L, 3L)
                .doesNotContainKey(8L);
    }

    @Test
    void create_withoutCreatePermission_throwsAccessDeniedException() {
        authenticate("role:view", "product:update");

        assertThatThrownBy(() -> roleService.create(request(AppPermission.PRODUCT_UPDATE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Missing permission role:create");
        verifyNoInteractions(roleRepository);
    }

    @Test
    void create_rejectsEqualPriority() {
        authenticate("role:create", "product:view");
        RoleRequest request = request(AppPermission.PRODUCT_VIEW);
        request.setPriority(100);

        assertThatThrownBy(() -> roleService.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("lower than your priority");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 101})
    void create_rejectsPrioritiesOutsideTheSupportedRange(Integer priority) {
        RoleRequest request = request(AppPermission.PRODUCT_VIEW);
        request.setPriority(priority);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);

        assertThatThrownBy(() -> directService().create(request))
                .isInstanceOf(RoleException.class)
                .hasMessage("Priority must be between 0 and 100.");
    }

    @Test
    void create_withUnassignablePermission_throwsRoleException() {
        authenticate("role:create", "product:view");

        assertThatThrownBy(() -> roleService.create(request(AppPermission.PRODUCT_DELETE)))
                .isInstanceOf(RoleException.class)
                .hasMessage("You cannot assign permissions that you do not have.");
    }

    @Test
    void reads_coverFiltersCountsMembersAndMissingRole() {
        RoleService service = directService();
        when(roleRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        assertThat(service.search(new RoleFilter(" QUERY ", false))).isEmpty();
        assertThat(service.search(null)).isEmpty();
        assertThat(service.search(new RoleFilter(null, null))).isEmpty();
        ArgumentCaptor<Specification<Role>> specifications = ArgumentCaptor.captor();
        verify(roleRepository, times(3)).findAll(specifications.capture(), any(Sort.class));
        assertThat(specifications.getAllValues())
                .extracting(SpecificationTestSupport::predicateCount)
                .containsExactly(2, 0, 0);

        when(roleRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role was not found.");
        assertThat(service.userCounts(List.of())).isEmpty();

        when(appUserRepository.findDistinctByRolesIdOrderByUsernameAsc(1L)).thenReturn(List.of());

        assertThat(service.members(1L)).isEmpty();
        assertThat(service.assignablePermissions()).containsExactly(AppPermission.values());
    }

    @Test
    void userCounts_mapsRepositoryValues() {
        when(appUserRepository.countMembersByRoleIds(Set.of(1L))).thenReturn(List.<Object[]>of(new Object[] {1L, 2L}));

        assertThat(directService().userCounts(Set.of(1L))).containsEntry(1L, 2L);
    }

    @Test
    void create_normalizesValuesAndRejectsDuplicateOrEmptyPermissions() {
        RoleService service = directService();
        when(authorizationService.canAll(any())).thenReturn(true);
        when(authorizationService.outranksPriority(10)).thenReturn(true);
        when(roleRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        RoleRequest request = request(AppPermission.PRODUCT_VIEW);
        request.setCode(" code ");
        request.setName(" Name ");
        request.setDescription("  ");
        request.setActive(false);

        Role role = service.create(request);

        assertThat(role.getCode()).isEqualTo("CODE");
        assertThat(role.getName()).isEqualTo("Name");
        assertThat(role.getDescription()).isNull();
        assertThat(role.isActive()).isFalse();

        request.setCode(null);
        request.setName(null);
        request.setDescription(" text ");

        assertThat(service.create(request).getDescription()).isEqualTo("text");

        when(roleRepository.existsByCodeIgnoreCase("")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("already exists");
        when(roleRepository.existsByCodeIgnoreCase("")).thenReturn(false);
        request.setPermissions(null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("one permission");
        request.setPermissions(Set.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("one permission");
        RoleRequest nullPermissions = mock(RoleRequest.class);
        when(nullPermissions.getPermissions()).thenReturn(null);

        assertThatThrownBy(() -> service.create(nullPermissions))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("one permission");
        request.setPermissions(Set.of(AppPermission.PRODUCT_VIEW));
        when(authorizationService.canAll(any())).thenReturn(false);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("cannot assign");
    }

    @Test
    void update_enforcesExistenceVersionCodeAndPriorityBoundaries() throws Exception {
        Role role = new Role("CODE", "Old", null);
        set(role);
        RoleRequest request = request(AppPermission.PRODUCT_VIEW);
        request.setCode(" code ");
        request.setName(" New ");
        request.setVersion(2L);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(any())).thenReturn(true);
        when(roleRepository.save(role)).thenReturn(role);

        assertThat(directService().update(1L, request).getName()).isEqualTo("New");
        verify(authorizationService).invalidateAll();

        request.setVersion(3L);

        assertThatThrownBy(() -> directService().update(1L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("another administrator");
        request.setVersion(2L);
        request.setCode("OTHER");

        assertThatThrownBy(() -> directService().update(1L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("cannot be changed");
        when(roleRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directService().update(2L, request))
                .isInstanceOf(RoleException.class)
                .hasMessage("Role was not found.");
        Role highest = new Role("SYS", "Highest", null);
        highest.update(highest.getName(), highest.getDescription(), 100, true, Set.of(AppPermission.PRODUCT_VIEW));
        set(highest);
        request.setCode("SYS");
        request.setName("Updated highest role");
        request.setPriority(90);
        request.setActive(true);
        when(roleRepository.findById(3L)).thenReturn(Optional.of(highest));
        when(authorizationService.canManagePriority(100)).thenReturn(true);

        assertThatThrownBy(() -> directService().update(3L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("cannot change priority");

        request.setPriority(100);
        request.setActive(false);

        assertThatThrownBy(() -> directService().update(3L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("active state");

        request.setActive(true);
        when(roleRepository.save(highest)).thenReturn(highest);

        Role updatedHighest = directService().update(3L, request);

        assertThat(updatedHighest.getName()).isEqualTo("Updated highest role");
        assertThat(updatedHighest.getPriority()).isEqualTo(100);
    }

    @Test
    void update_requiresTheActorToOutrankChangedPriority() throws Exception {
        Role role = UserDomainTest.role("ROLE", true, AppPermission.PRODUCT_VIEW);
        set(role);
        RoleRequest request = request(AppPermission.PRODUCT_VIEW);
        request.setCode("ROLE");
        request.setVersion(2L);
        request.setPriority(20);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);

        assertThatThrownBy(() -> directService().update(1L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("lower than your priority");

        when(authorizationService.outranksPriority(20)).thenReturn(true);
        when(roleRepository.save(role)).thenReturn(role);

        assertThat(directService().update(1L, request).getPriority()).isEqualTo(20);
    }

    @Test
    void update_requiresTheActorToOutrankAnActiveStateChange() throws Exception {
        Role role = UserDomainTest.role("ROLE", true, AppPermission.PRODUCT_VIEW);
        set(role);
        RoleRequest request = request(AppPermission.PRODUCT_VIEW);
        request.setCode("ROLE");
        request.setVersion(2L);
        request.setActive(false);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);

        assertThatThrownBy(() -> directService().update(1L, request))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("active state");

        when(authorizationService.outranksPriority(10)).thenReturn(true);
        when(roleRepository.save(role)).thenReturn(role);

        assertThat(directService().update(1L, request).isActive()).isFalse();
    }

    @Test
    void deactivate_requiresStrictlyHigherPriority() {
        Role custom = new Role("C", "Custom", null);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(custom));
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.outranksPriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of())).thenReturn(true);

        directService().deactivate(1L);

        assertThat(custom.isActive()).isFalse();
        verify(roleRepository).save(custom);
        verify(authorizationService).invalidateAll();

        when(roleRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> directService().deactivate(2L)).isInstanceOf(RoleException.class);

        Role highest = new Role("S", "Highest", null);
        highest.update(highest.getName(), highest.getDescription(), 100, true, Set.of());
        when(roleRepository.findById(3L)).thenReturn(Optional.of(highest));
        when(authorizationService.canManagePriority(100)).thenReturn(true);
        when(authorizationService.outranksPriority(100)).thenReturn(false);

        assertThatThrownBy(() -> directService().deactivate(3L))
                .isInstanceOf(RoleException.class)
                .hasMessageContaining("lower than your priority");
    }

    @Test
    void capabilitiesDelegateToAuthorization() {
        when(authorizationService.can(AppPermission.ROLE_CREATE)).thenReturn(true);
        when(authorizationService.can(AppPermission.ROLE_UPDATE)).thenReturn(false);
        when(authorizationService.can(AppPermission.ROLE_DELETE)).thenReturn(true);
        when(authorizationService.can(AppPermission.ROLE_ASSIGN)).thenReturn(true);

        assertThat(directService().canCreateRoles()).isTrue();
        assertThat(directService().canUpdateRoles()).isFalse();
        assertThat(directService().canDeleteRoles()).isTrue();
        assertThat(directService().canAssignRoles()).isTrue();
        verify(authorizationService).can(AppPermission.ROLE_CREATE);
        verify(authorizationService).can(AppPermission.ROLE_UPDATE);
    }

    @Test
    void canUpdateRoleRequiresPermissionManageablePriorityAndOwnedPermissions() {
        Role manageable = UserDomainTest.role("MANAGEABLE", true, AppPermission.PRODUCT_VIEW);
        Role priorityBlocked = roleAtPriority("PRIORITY_BLOCKED", 20, AppPermission.PRODUCT_VIEW);
        Role permissionBlocked = UserDomainTest.role("PERMISSION_BLOCKED", true, AppPermission.PRODUCT_DELETE);

        assertThat(directService().canUpdateRole(manageable)).isFalse();

        when(authorizationService.can(AppPermission.ROLE_UPDATE)).thenReturn(true);
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canManagePriority(20)).thenReturn(false);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_DELETE))).thenReturn(false);

        assertThat(directService().canUpdateRole(manageable)).isTrue();
        assertThat(directService().canUpdateRole(priorityBlocked)).isFalse();
        assertThat(directService().canUpdateRole(permissionBlocked)).isFalse();
    }

    @Test
    void canChangeActiveStateAlsoRequiresStrictlyHigherPriority() {
        Role role = UserDomainTest.role("ROLE", true, AppPermission.PRODUCT_VIEW);

        assertThat(directService().canChangeActiveState(role)).isFalse();

        when(authorizationService.can(AppPermission.ROLE_UPDATE)).thenReturn(true);
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);

        assertThat(directService().canChangeActiveState(role)).isFalse();

        when(authorizationService.outranksPriority(10)).thenReturn(true);

        assertThat(directService().canChangeActiveState(role)).isTrue();
    }

    @Test
    void canDeactivateRoleRequiresPermissionManageableRoleAndStrictlyHigherPriority() {
        Role manageable = UserDomainTest.role("MANAGEABLE", true, AppPermission.PRODUCT_VIEW);
        Role priorityBlocked = roleAtPriority("PRIORITY_BLOCKED", 20, AppPermission.PRODUCT_VIEW);
        Role permissionBlocked = UserDomainTest.role("PERMISSION_BLOCKED", true, AppPermission.PRODUCT_DELETE);

        assertThat(directService().canDeactivateRole(manageable)).isFalse();

        when(authorizationService.can(AppPermission.ROLE_DELETE)).thenReturn(true);
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canManagePriority(20)).thenReturn(false);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_DELETE))).thenReturn(false);

        assertThat(directService().canDeactivateRole(priorityBlocked)).isFalse();
        assertThat(directService().canDeactivateRole(permissionBlocked)).isFalse();
        assertThat(directService().canDeactivateRole(manageable)).isFalse();

        when(authorizationService.outranksPriority(10)).thenReturn(true);

        assertThat(directService().canDeactivateRole(manageable)).isTrue();
    }

    @Test
    void canAssignRoleRequiresPermissionAndAManageableRole() {
        Role manageable = UserDomainTest.role("MANAGEABLE", true, AppPermission.PRODUCT_VIEW);
        Role priorityBlocked = roleAtPriority("PRIORITY_BLOCKED", 20, AppPermission.PRODUCT_VIEW);

        assertThat(directService().canAssignRole(manageable)).isFalse();

        when(authorizationService.can(AppPermission.ROLE_ASSIGN)).thenReturn(true);
        when(authorizationService.canManagePriority(10)).thenReturn(true);
        when(authorizationService.canManagePriority(20)).thenReturn(false);
        when(authorizationService.canAll(Set.of(AppPermission.PRODUCT_VIEW))).thenReturn(true);

        assertThat(directService().canAssignRole(priorityBlocked)).isFalse();
        assertThat(directService().canAssignRole(manageable)).isTrue();
    }

    private RoleService directService() {
        return new RoleService(roleRepository, appUserRepository, authorizationService);
    }

    private static void set(Object target) throws Exception {
        set(target, "version", 2L);
    }

    private static void set(Object target, String field, Object value) throws Exception {
        var declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    private Role roleAtPriority(String code, int priority, AppPermission... permissions) {
        Role role = UserDomainTest.role(code, true, permissions);
        role.update(role.getName(), role.getDescription(), priority, true, role.getPermissions());

        return role;
    }

    private RoleRequest request(AppPermission... permissions) {
        var request = new RoleRequest();
        request.setCode("catalog_editor");
        request.setName("Catalog Editor");
        request.setPermissions(new LinkedHashSet<>(Arrays.asList(permissions)));

        return request;
    }

    private void authenticate(String... permissionCodes) {
        var permissions = Arrays.stream(permissionCodes)
                .map(AppPermission::fromCode)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Role role = new Role("ADMIN", "Admin", null);
        role.update(role.getName(), role.getDescription(), 100, true, permissions);
        AppUser user = new AppUser("admin", "admin@example.com", "issuer", "subject");
        user.addRole(role);
        when(appUserRepository.findForAuthorization("admin")).thenReturn(Optional.of(user));

        var authorities =
                Arrays.stream(permissionCodes).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("admin", "password", authorities));
    }
}
