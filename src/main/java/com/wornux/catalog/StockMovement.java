package com.wornux.catalog;

import com.wornux.audit.Auditable;
import com.wornux.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "stock_movement")
@Audited
public class StockMovement extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "movement_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private Integer quantityDelta;

    private String reason;

    protected StockMovement() {}

    public StockMovement(
            Product product, AppUser user, MovementType movementType, Integer quantityDelta, String reason) {
        this.product = product;
        this.user = user;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public AppUser getUser() {
        return user;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public Integer getQuantityDelta() {
        return quantityDelta;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return super.getCreatedAt();
    }
}
