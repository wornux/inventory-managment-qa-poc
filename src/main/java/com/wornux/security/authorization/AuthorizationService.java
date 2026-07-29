package com.wornux.security.authorization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AuthorizationService {

    private final AppUserRepository appUserRepository;
    private final Cache<String, UserAccessSnapshot> cache;

    public AuthorizationService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(20))
                .build();
    }

    public boolean can(AppPermission permission) {
        return canAll(Set.of(permission));
    }

    public boolean canAll(Collection<AppPermission> permissions) {
        Set<AppPermission> granted = assignedPermissions();

        return permissions.stream()
                .allMatch(requested -> granted.stream().anyMatch(permission -> permission.grants(requested)));
    }

    public Set<AppPermission> effectivePermissions() {
        Set<AppPermission> granted = assignedPermissions();

        return Arrays.stream(AppPermission.values())
                .filter(requested -> granted.stream().anyMatch(permission -> permission.grants(requested)))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean canManagePriority(int targetPriority) {
        return currentSnapshot()
                .filter(UserAccessSnapshot::active)
                .map(snapshot -> targetPriority <= snapshot.highestRolePriority())
                .orElse(false);
    }

    public boolean outranksPriority(int targetPriority) {
        return currentSnapshot()
                .filter(UserAccessSnapshot::active)
                .map(snapshot -> targetPriority < snapshot.highestRolePriority())
                .orElse(false);
    }

    public void check(AppPermission permission) {
        if (!can(permission)) {
            throw new AccessDeniedException("Missing permission " + permission.code());
        }
    }

    public Optional<UserAccessSnapshot> cached(String principal) {
        return Optional.ofNullable(cache.getIfPresent(normalize(principal)));
    }

    public UserAccessSnapshot cache(AppUser user) {
        UserAccessSnapshot snapshot = snapshot(user);
        cache.put(normalize(user.getUsername()), snapshot);

        return snapshot;
    }

    public void invalidateUser(Long userId) {
        afterCommit(() -> cache.asMap()
                .entrySet()
                .removeIf(entry -> Objects.equals(entry.getValue().userId(), userId)));
    }

    public void invalidateAll() {
        afterCommit(cache::invalidateAll);
    }

    private Set<AppPermission> assignedPermissions() {
        return currentSnapshot()
                .filter(UserAccessSnapshot::active)
                .map(UserAccessSnapshot::permissions)
                .orElseGet(Set::of);
    }

    private Optional<UserAccessSnapshot> currentSnapshot() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(cache.get(normalize(authentication.getName()), this::load));
    }

    private UserAccessSnapshot load(String principal) {
        return appUserRepository
                .findForAuthorization(principal)
                .map(this::snapshot)
                .orElse(null);
    }

    private UserAccessSnapshot snapshot(AppUser user) {
        var activeRoles = user.getRoles().stream().filter(Role::isActive).toList();
        var permissions = new LinkedHashSet<AppPermission>();
        activeRoles.stream().flatMap(role -> role.getPermissions().stream()).forEach(permissions::add);
        int highestRolePriority =
                activeRoles.stream().mapToInt(Role::getPriority).max().orElse(-1);

        return new UserAccessSnapshot(
                user.getId(), user.getUsername(), user.isActive(), highestRolePriority, permissions);
    }

    private void afterCommit(Runnable invalidation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidation.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidation.run();
            }
        });
    }

    private String normalize(String principal) {
        return principal == null ? "" : principal.trim().toLowerCase(Locale.ROOT);
    }
}
