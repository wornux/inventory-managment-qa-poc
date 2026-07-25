package com.wornux.user;

import com.wornux.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "app_user")
@Audited
public class AppUser extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "oidc_issuer")
    private String oidcIssuer;

    @Column(name = "oidc_subject")
    private String oidcSubject;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected AppUser() {}

    public AppUser(String username, String email, String oidcIssuer, String oidcSubject) {
        this.username = username;
        this.email = email;
        this.oidcIssuer = oidcIssuer;
        this.oidcSubject = oidcSubject;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getOidcIssuer() {
        return oidcIssuer;
    }

    public String getOidcSubject() {
        return oidcSubject;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return super.getCreatedAt();
    }

    public Instant getUpdatedAt() {
        return super.getUpdatedAt();
    }

    public Long getVersion() {
        return version;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public void update(String username, String email, boolean active, Set<Role> roles) {
        this.username = username;
        this.email = email;
        this.active = active;
        this.roles.clear();
        this.roles.addAll(roles);
    }

    public void updateIdentity(String username, String email, String oidcIssuer, String oidcSubject) {
        this.username = username;
        this.email = email;
        this.oidcIssuer = oidcIssuer;
        this.oidcSubject = oidcSubject;
    }

    public void deactivate() {
        active = false;
    }
}
