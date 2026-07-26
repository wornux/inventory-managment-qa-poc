package com.wornux.api.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.catalog.Category;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductFilter;
import com.wornux.catalog.ProductService;
import com.wornux.catalog.Supplier;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ProductApiTest {
    @Mock
    ProductService service;

    @Mock
    ProductApiMapper mapper;

    @Test
    void mapperConvertsRequestsAndCompleteAndNullableReferences() {
        var real = new ProductApiMapper(JsonMapper.builderWithJackson2Defaults().build());
        var request = request();

        var domain = real.toDomainRequest(request);

        assertThat(domain)
                .extracting(
                        value -> value.getSku(),
                        value -> value.getName(),
                        value -> value.getDescription(),
                        value -> value.getUnitPrice(),
                        value -> value.getQuantityOnHand(),
                        value -> value.getMinimumStock(),
                        value -> value.getCategoryId(),
                        value -> value.getSupplierId(),
                        value -> value.isActive(),
                        value -> value.getVersion())
                .containsExactly("SKU", "Hammer", "desc", new BigDecimal("12.50"), 2, 3, 4L, 5L, true, 6L);

        var category = mock(Category.class);
        var supplier = mock(Supplier.class);
        when(category.getId()).thenReturn(4L);
        when(category.getName()).thenReturn("Tools");
        when(supplier.getId()).thenReturn(5L);
        when(supplier.getName()).thenReturn("Acme");
        var product = mock(Product.class);
        when(product.getId()).thenReturn(9L);
        when(product.getSku()).thenReturn("SKU");
        when(product.getName()).thenReturn("Hammer");
        when(product.getDescription()).thenReturn("desc");
        when(product.getUnitPrice()).thenReturn(new BigDecimal("12.50"));
        when(product.getQuantityOnHand()).thenReturn(2);
        when(product.getMinimumStock()).thenReturn(3);
        when(product.isActive()).thenReturn(true);
        when(product.getVersion()).thenReturn(6L);
        when(product.isLowStock()).thenReturn(true);
        when(product.getCategory()).thenReturn(category);
        when(product.getSupplier()).thenReturn(supplier);

        assertThat(real.toResponse(product))
                .isEqualTo(new ProductResponseDto(
                        9L,
                        "SKU",
                        "Hammer",
                        "desc",
                        new BigDecimal("12.50"),
                        2,
                        3,
                        true,
                        6L,
                        true,
                        new CatalogReferenceResponseDto(4L, "Tools"),
                        new CatalogReferenceResponseDto(5L, "Acme")));

        when(product.getCategory()).thenReturn(null);
        when(product.getSupplier()).thenReturn(null);

        assertThat(real.toResponse(product))
                .extracting(ProductResponseDto::category, ProductResponseDto::supplier)
                .containsExactly(null, null);
    }

    @Test
    void controllerListBuildsFilterMapsResultsAndReturnsPageMetadata() {
        var product = mock(Product.class);
        var response = new ProductResponseDto(null, "s", "n", null, BigDecimal.ONE, 1, 1, true, null, true, null, null);
        var pageable = PageRequest.of(1, 2);
        when(service.search(any(), eq(pageable))).thenReturn(new PageImpl<>(List.of(product), pageable, 3));
        when(mapper.toResponse(product)).thenReturn(response);

        var result = new ProductController(service, mapper).list("x", 1L, 2L, true, true, pageable);

        assertThat(result.getBody().data()).containsExactly(response);
        assertThat(result.getBody().page().totalElements()).isEqualTo(3);

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);

        verify(service).search(filter.capture(), eq(pageable));
        assertThat(filter.getValue())
                .extracting("text", "categoryId", "supplierId", "active", "lowStockOnly")
                .containsExactly("x", 1L, 2L, true, true);
    }

    @Test
    void controllerDelegatesGetCreateUpdateAndDelete() {
        var domainRequest = mock(com.wornux.catalog.ProductRequest.class);
        var product = mock(Product.class);
        var response = new ProductResponseDto(9L, "s", "n", null, BigDecimal.ONE, 1, 1, true, 0L, true, null, null);
        when(mapper.toDomainRequest(any())).thenReturn(domainRequest);
        when(mapper.toResponse(product)).thenReturn(response);
        when(product.getId()).thenReturn(9L);
        when(service.get(9L)).thenReturn(product);
        when(service.create(domainRequest)).thenReturn(product);
        when(service.update(9L, domainRequest)).thenReturn(product);
        var controller = new ProductController(service, mapper);

        assertThat(controller.get(9L).getBody().data()).isEqualTo(response);
        assertThat(controller.create(request()).getHeaders().getLocation()).hasPath("/api/products/9");
        assertThat(controller.update(9L, request()).getBody().message()).isEqualTo("Product updated.");
        assertThat(controller.delete(9L).getBody().message()).isEqualTo("Product deleted or deactivated.");
        verify(service).delete(9L);
    }

    private static ProductRequestDto request() {
        return new ProductRequestDto("SKU", "Hammer", "desc", new BigDecimal("12.50"), 2, 3, 4L, 5L, true, 6L);
    }
}
