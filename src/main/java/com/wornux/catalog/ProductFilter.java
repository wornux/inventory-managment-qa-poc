package com.wornux.catalog;

public record ProductFilter(String text, Long categoryId, Long supplierId, Boolean active, boolean lowStockOnly) {}
