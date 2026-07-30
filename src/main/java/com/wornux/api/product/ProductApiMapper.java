package com.wornux.api.product;

import com.wornux.catalog.Category;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductRequest;
import com.wornux.catalog.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductApiMapper {

    ProductRequest toDomainRequest(ProductRequestDto request);

    ProductResponseDto toResponse(Product product);

    CatalogReferenceResponseDto toReference(Category category);

    CatalogReferenceResponseDto toReference(Supplier supplier);
}
