package com.wornux.ui.security;

import com.wornux.user.AppUserRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UiAccessService {

    private static final String READ = "READ";

    private static final Map<String, Set<String>> FALLBACK_READ_ROLES = Map.of(
            "PRODUCT", Set.of("ROLE_INVENTORY_VIEWER", "ROLE_INVENTORY_MANAGER", "ROLE_SYSTEM_ADMINISTRATOR"),
            "CATEGORY", Set.of("ROLE_INVENTORY_VIEWER", "ROLE_INVENTORY_MANAGER", "ROLE_SYSTEM_ADMINISTRATOR"),
            "SUPPLIER", Set.of("ROLE_INVENTORY_VIEWER", "ROLE_INVENTORY_MANAGER", "ROLE_SYSTEM_ADMINISTRATOR"),
            "STOCK_MOVEMENT", Set.of(
                    "ROLE_INVENTORY_VIEWER",
                    "ROLE_WAREHOUSE_OPERATOR",
                    "ROLE_INVENTORY_MANAGER",
                    "ROLE_SYSTEM_ADMINISTRATOR"),
            "USER", Set.of("ROLE_SYSTEM_ADMINISTRATOR"),
            "ROLE", Set.of("ROLE_SYSTEM_ADMINISTRATOR"),
            "PERMISSION", Set.of("ROLE_SYSTEM_ADMINISTRATOR"));

    private final AppUserRepository appUserRepository;

    public UiAccessService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public boolean canRead(String resourceCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean databasePermission =
                appUserRepository.hasActivePermission(authentication.getName(), resourceCode, READ);
        if (isDatabaseUser(authentication)) {
            return databasePermission;
        }

        return hasFallbackRole(authentication, resourceCode);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyBusinessModule() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean databaseUser = isDatabaseUser(authentication);
        return FALLBACK_READ_ROLES.keySet().stream().anyMatch(resourceCode -> {
            boolean databasePermission =
                    appUserRepository.hasActivePermission(authentication.getName(), resourceCode, READ);
            if (databaseUser) {
                return databasePermission;
            }

            return hasFallbackRole(authentication, resourceCode);
        });
    }

    private boolean isDatabaseUser(Authentication authentication) {
        String principal = authentication.getName();
        return appUserRepository.existsByUsernameIgnoreCase(principal)
                || appUserRepository.existsByEmailIgnoreCase(principal);
    }

    private boolean hasFallbackRole(Authentication authentication, String resourceCode) {
        return FALLBACK_READ_ROLES.getOrDefault(resourceCode, Set.of()).stream()
                .anyMatch(required -> authentication.getAuthorities().stream()
                        .anyMatch(authority -> required.equals(authority.getAuthority())));
    }
}
