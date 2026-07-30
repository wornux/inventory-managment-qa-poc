package com.wornux.catalog;

import com.wornux.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "supplier")
@Audited
public class Supplier extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_name")
    private String contactName;

    private String email;

    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    private Long version;

    protected Supplier() {}

    public Supplier(String name, String contactName, String email, String phone) {
        this.name = name;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContactName() {
        return contactName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public void update(String name, String contactName, String email, String phone, boolean active) {
        this.name = name;
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.active = active;
    }

    public void deactivate() {
        active = false;
    }
}
