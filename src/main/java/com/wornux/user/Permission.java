package com.wornux.user;

import com.wornux.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "permission")
@Audited
public class Permission extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "resource_id")
    private ProtectedResource resource;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "action_id")
    private PermissionAction action;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    protected Permission() {
    }

    public Permission(ProtectedResource resource, PermissionAction action, String description, boolean active) {
        this.resource = resource;
        this.action = action;
        this.description = description;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public ProtectedResource getResource() {
        return resource;
    }

    public PermissionAction getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return super.getCreatedAt();
    }

    public Instant getUpdatedAt() {
        return super.getUpdatedAt();
    }

    public String getCode() {
        return resource.getCode() + ":" + action.getCode();
    }

    public String getLabel() {
        return getCode() + " - " + resource.getName() + " / " + action.getName();
    }

    public void update(String description, boolean active) {
        this.description = description;
        this.active = active;
    }

    public void deactivate() {
        active = false;
    }
}
