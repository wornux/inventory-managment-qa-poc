package com.wornux.security.authorization;

import com.wornux.security.permission.AppPermission;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record UserAccessSnapshot(
        Long userId, String username, boolean active, int highestRolePriority, Set<AppPermission> permissions) {

    public UserAccessSnapshot {
        permissions = Set.copyOf(permissions);
    }

    public List<GrantedAuthority> authorities() {
        return permissions.stream()
                .map(AppPermission::code)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
