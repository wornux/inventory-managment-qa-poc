package com.wornux.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    @AfterEach
    void clearThreadLocalState() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void can_reusesSnapshotUntilRoleChangeInvalidatesIt() {
        Role role = new Role("VIEWER", "Viewer", null);
        role.update(role.getName(), role.getDescription(), true, Set.of(AppPermission.PRODUCT_VIEW));
        AppUser user = new AppUser("viewer", "viewer@example.com", "issuer", "subject");
        user.addRole(role);
        when(appUserRepository.findForAuthorization("viewer")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("viewer", "password", List.of()));
        var authorizationService = new AuthorizationService(appUserRepository);

        assertThat(authorizationService.can(AppPermission.PRODUCT_VIEW)).isTrue();

        role.deactivate();

        assertThat(authorizationService.can(AppPermission.PRODUCT_VIEW)).isTrue();

        authorizationService.invalidateAll();

        assertThat(authorizationService.can(AppPermission.PRODUCT_VIEW)).isFalse();
        verify(appUserRepository, times(2)).findForAuthorization("viewer");
    }

    @Test
    void unauthenticatedAndMissingUsersHaveNoPermissions() {
        var service = new AuthorizationService(appUserRepository);

        assertThat(service.canAll(Set.of())).isTrue();
        assertThat(service.can(AppPermission.PRODUCT_VIEW)).isFalse();
        verify(appUserRepository, never()).findForAuthorization(any());

        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("missing", "password"));

        assertThat(service.can(AppPermission.PRODUCT_VIEW)).isFalse();

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("missing", "password", List.of()));
        when(appUserRepository.findForAuthorization("missing")).thenReturn(Optional.empty());

        assertThat(service.can(AppPermission.PRODUCT_VIEW)).isFalse();
    }

    @Test
    void actionPermissionGrantsViewButNotAnotherResourceAndCheckRejectsMissingPermission() {
        Role role = new Role("EDITOR", "Editor", null);
        role.update(role.getName(), role.getDescription(), true, Set.of(AppPermission.PRODUCT_UPDATE));
        AppUser user = new AppUser("editor", "editor@example.com", "issuer", "subject");
        user.addRole(role);
        when(appUserRepository.findForAuthorization("editor")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("editor", "password", List.of()));
        var service = new AuthorizationService(appUserRepository);

        assertThat(service.canAll(Set.of(AppPermission.PRODUCT_UPDATE, AppPermission.PRODUCT_VIEW)))
                .isTrue();
        assertThat(service.can(AppPermission.CATEGORY_VIEW)).isFalse();

        service.check(AppPermission.PRODUCT_VIEW);

        assertThatThrownBy(() -> service.check(AppPermission.PRODUCT_DELETE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Missing permission product:delete");
    }

    @Test
    void effectivePermissionsIncludesCapabilitiesImpliedByAssignedActions() {
        Role role = new Role("EDITOR", "Editor", null);
        role.update(
                role.getName(),
                role.getDescription(),
                true,
                Set.of(AppPermission.PRODUCT_UPDATE, AppPermission.STOCK_MOVEMENT_CREATE));
        AppUser user = new AppUser("editor", "editor@example.com", "issuer", "subject");
        user.addRole(role);
        when(appUserRepository.findForAuthorization("editor")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("editor", "password", List.of()));

        assertThat(new AuthorizationService(appUserRepository).effectivePermissions())
                .containsExactlyInAnyOrder(
                        AppPermission.PRODUCT_VIEW,
                        AppPermission.PRODUCT_UPDATE,
                        AppPermission.STOCK_MOVEMENT_VIEW,
                        AppPermission.STOCK_MOVEMENT_CREATE);
    }

    @Test
    void inactiveUserHasNoPermissions() {
        AppUser user = new AppUser("disabled", "disabled@example.com", "issuer", "subject");
        user.deactivate();
        when(appUserRepository.findForAuthorization("disabled")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("disabled", "password", List.of()));

        assertThat(new AuthorizationService(appUserRepository).can(AppPermission.PRODUCT_VIEW))
                .isFalse();
    }

    @Test
    void authenticationWithoutANameHasNoPermissions() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(new AuthorizationService(appUserRepository).can(AppPermission.PRODUCT_VIEW))
                .isFalse();
        verify(appUserRepository, never()).findForAuthorization(any());
    }

    @Test
    void cacheNormalizesPrincipalAndReturnsTheStoredSnapshot() {
        AppUser user = mockUser(7L, "  Editor  ");
        var service = new AuthorizationService(appUserRepository);

        UserAccessSnapshot snapshot = service.cache(user);

        assertThat(service.cached(" EDITOR ")).contains(snapshot);
        assertThat(service.cached(null)).isEmpty();
    }

    @Test
    void invalidateUserRemovesOnlyTheMatchingSnapshot() {
        var service = new AuthorizationService(appUserRepository);
        service.cache(mockUser(7L, "first"));
        UserAccessSnapshot retained = service.cache(mockUser(8L, "second"));

        service.invalidateUser(7L);

        assertThat(service.cached("first")).isEmpty();
        assertThat(service.cached("second")).contains(retained);
    }

    @Test
    void invalidationWaitsForAnActiveTransactionToCommit() {
        var service = new AuthorizationService(appUserRepository);
        UserAccessSnapshot snapshot = service.cache(mockUser(7L, "editor"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.invalidateUser(7L);

        assertThat(service.cached("editor")).contains(snapshot);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertThat(service.cached("editor")).isEmpty();
    }

    @Test
    void invalidationRunsImmediatelyWithoutTransactionSynchronization() {
        var service = new AuthorizationService(appUserRepository);
        service.cache(mockUser(7L, "editor"));
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.invalidateAll();

        assertThat(service.cached("editor")).isEmpty();
    }

    private static AppUser mockUser(Long id, String username) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.isActive()).thenReturn(true);
        when(user.getRoles()).thenReturn(Set.of());

        return user;
    }
}
