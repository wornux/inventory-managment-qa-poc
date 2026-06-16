package com.wornux.catalog;

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
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "product")
@Audited
public class Product extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "quantity_on_hand", nullable = false)
    private Integer quantityOnHand = 0;

    @Column(name = "minimum_stock", nullable = false)
    private Integer minimumStock = 0;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Version
    private Long version;

    protected Product() {
    }

    public Product(
            String sku,
            String name,
            String description,
            BigDecimal unitPrice,
            Integer quantityOnHand,
            Integer minimumStock,
            Category category,
            Supplier supplier,
            boolean active) {
        update(sku, name, description, unitPrice, quantityOnHand, minimumStock, category, supplier, active);
    }

    public void update(
            String sku,
            String name,
            String description,
            BigDecimal unitPrice,
            Integer quantityOnHand,
            Integer minimumStock,
            Category category,
            Supplier supplier,
            boolean active) {
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.unitPrice = unitPrice;
        this.quantityOnHand = quantityOnHand;
        this.minimumStock = minimumStock;
        this.category = category;
        this.supplier = supplier;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Integer getQuantityOnHand() {
        return quantityOnHand;
    }

    public Integer getMinimumStock() {
        return minimumStock;
    }

    public boolean isActive() {
        return active;
    }

    public Category getCategory() {
        return category;
    }

    public Supplier getSupplier() {
        return supplier;
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

    public boolean isLowStock() {
        return quantityOnHand <= minimumStock;
    }

    public String getStockStatus() {
        return isLowStock() ? "LOW STOCK" : "OK";
    }

    public void deactivate() {
        active = false;
    }

    public void applyQuantityDelta(int quantityDelta) {
        quantityOnHand += quantityDelta;
    }
}
