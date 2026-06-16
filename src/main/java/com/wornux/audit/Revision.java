package com.wornux.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionMapping;

@Entity
@Table(name = "revision")
@RevisionEntity(RevisionListenerImpl.class)
public class Revision extends RevisionMapping {

    @Column(name = "modifier_user")
    protected String modifierUser;

    @Column(name = "ip_address")
    protected String ipAddress;

    public String getModifierUser() {
        return modifierUser;
    }

    public void setModifierUser(String modifierUser) {
        this.modifierUser = modifierUser;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @Override
    public String toString() {
        return "Revision{"
                + "id=" + getId()
                + ", timestamp=" + getTimestamp()
                + ", modifierUser='" + modifierUser + '\''
                + ", ipAddress='" + ipAddress + '\''
                + '}';
    }
}
