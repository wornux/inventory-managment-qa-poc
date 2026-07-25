package com.wornux.user;

import com.wornux.security.permission.AppPermission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

public class RoleRequest {

    @NotBlank(message = "Role code is required.")
    private String code;

    @NotBlank(message = "Role name is required.")
    private String name;

    @Size(max = 500, message = "Description must be 500 characters or fewer.")
    private String description;

    private boolean active = true;
    private Set<AppPermission> permissions = new LinkedHashSet<>();
    private Long version;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<AppPermission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<AppPermission> permissions) {
        this.permissions = permissions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
