package com.wornux.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class ProductRequest {

    @NotBlank(message = "SKU is required.")
    private String sku;

    @NotBlank(message = "Product name is required.")
    private String name;

    private String description;

    @NotNull(message = "Unit price is required.")
    @DecimalMin(value = "0.00", message = "Unit price must be a positive number.")
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @NotNull(message = "Quantity on hand is required.")
    @Min(value = 0, message = "Quantity on hand must be zero or greater.")
    private Integer quantityOnHand = 0;

    @NotNull(message = "Minimum stock is required.")
    @Min(value = 0, message = "Minimum stock must be zero or greater.")
    private Integer minimumStock = 0;

    @NotNull(message = "Category is required.")
    private Long categoryId;

    private Long supplierId;

    private boolean active = true;

    private Long version;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getQuantityOnHand() {
        return quantityOnHand;
    }

    public void setQuantityOnHand(Integer quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
    }

    public Integer getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(Integer minimumStock) {
        this.minimumStock = minimumStock;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
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
