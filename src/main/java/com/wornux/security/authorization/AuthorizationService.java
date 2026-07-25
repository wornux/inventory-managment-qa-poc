package com.wornux.security.authorization;

import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {

    private final AppUserRepository appUserRepository;

    public AuthorizationService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public boolean can(AppPermission permission) {
        return canAll(Set.of(permission));
    }

    @Transactional(readOnly = true)
    public boolean canAll(Collection<AppPermission> permissions) {
        Set<AppPermission> granted = currentPermissions();

        return permissions.stream()
                .allMatch(requested -> granted.stream().anyMatch(permission -> permission.grants(requested)));
    }

    public void check(AppPermission permission) {
        if (!can(permission)) {
            throw new AccessDeniedException("Missing permission " + permission.code());
        }
    }

    private Set<AppPermission> currentPermissions() {
        // ponytail: query per check keeps revocation immediate; add versioned snapshots only if this becomes a measured
        // bottleneck.
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            return Set.of();
        }

        return appUserRepository
                .findForAuthorization(authentication.getName())
                .filter(AppUser::isActive)
                .map(user -> {
                    var permissions = new LinkedHashSet<AppPermission>();
                    user.getRoles().stream()
                            .filter(Role::isActive)
                            .flatMap(role -> role.getPermissions().stream())
                            .forEach(permissions::add);

                    return Set.copyOf(permissions);
                })
                .orElseGet(Set::of);
    }
}
