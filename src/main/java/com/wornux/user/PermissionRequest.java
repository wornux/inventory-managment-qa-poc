package com.wornux.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PermissionRequest {

    @NotNull(message = "Resource is required.")
    private Long resourceId;

    @NotNull(message = "Action is required.")
    private Long actionId;

    @Size(max = 500, message = "Description must be 500 characters or fewer.")
    private String description;

    private boolean active = true;

    private Long version;

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Long getActionId() {
        return actionId;
    }

    public void setActionId(Long actionId) {
        this.actionId = actionId;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
