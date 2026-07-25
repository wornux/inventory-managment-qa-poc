package com.wornux.api.product;

import com.wornux.catalog.Category;
import com.wornux.catalog.Product;
import com.wornux.catalog.Supplier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProductApiMapper {

    private final JsonMapper jsonMapper;

    public ProductApiMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public com.wornux.catalog.ProductRequest toDomainRequest(ProductRequest request) {
        return jsonMapper.convertValue(request, com.wornux.catalog.ProductRequest.class);
    }

    public ProductResponse toResponse(Product product) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", product.getId());
        value.put("sku", product.getSku());
        value.put("name", product.getName());
        value.put("description", product.getDescription());
        value.put("unitPrice", product.getUnitPrice());
        value.put("quantityOnHand", product.getQuantityOnHand());
        value.put("minimumStock", product.getMinimumStock());
        value.put("active", product.isActive());
        value.put("version", product.getVersion());
        value.put("lowStock", product.isLowStock());
        value.put("category", reference(product.getCategory()));
        value.put("supplier", reference(product.getSupplier()));

        return jsonMapper.convertValue(value, ProductResponse.class);
    }

    private CatalogReferenceResponse reference(Category category) {
        if (category == null) {
            return null;
        }

        return jsonMapper.convertValue(
                Map.of("id", category.getId(), "name", category.getName()), CatalogReferenceResponse.class);
    }

    private CatalogReferenceResponse reference(Supplier supplier) {
        if (supplier == null) {
            return null;
        }

        return jsonMapper.convertValue(
                Map.of("id", supplier.getId(), "name", supplier.getName()), CatalogReferenceResponse.class);
    }
}
