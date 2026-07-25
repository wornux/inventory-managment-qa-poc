package com.wornux.user;

import com.wornux.audit.Auditable;
import com.wornux.security.permission.AppPermission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "role")
@Audited
public class Role extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole = true;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] permissions = new String[0];

    protected Role() {}

    public Role(String code, String name, String description) {
        this(code, name, description, true);
    }

    public Role(String code, String name, String description, boolean systemRole) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public Set<AppPermission> getPermissions() {
        var result = new LinkedHashSet<AppPermission>();
        Arrays.stream(permissions)
                .map(AppPermission::fromCode)
                .flatMap(Optional::stream)
                .forEach(result::add);

        return result;
    }

    public Instant getCreatedAt() {
        return super.getCreatedAt();
    }

    public Instant getUpdatedAt() {
        return super.getUpdatedAt();
    }

    public void update(String name, String description, boolean active, Set<AppPermission> permissions) {
        this.name = name;
        this.description = description;
        this.active = active;
        this.permissions = permissions.stream().map(AppPermission::code).toArray(String[]::new);
    }

    public void deactivate() {
        active = false;
    }
}
