package com.wornux.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.api.product.CatalogReferenceResponseDto;
import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryFilter;
import com.wornux.catalog.CategoryService;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierFilter;
import com.wornux.catalog.SupplierService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogLookupControllerTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private SupplierService supplierService;

    @Test
    void listsCompactFilteredCategoryAndSupplierOptions() {
        Category category = mock(Category.class);
        Supplier supplier = mock(Supplier.class);
        when(category.getId()).thenReturn(1L);
        when(category.getName()).thenReturn("Tools");
        when(supplier.getId()).thenReturn(2L);
        when(supplier.getName()).thenReturn("Acme");
        when(categoryService.search(new CategoryFilter("tool", true))).thenReturn(List.of(category));
        when(supplierService.search(new SupplierFilter("acme", true))).thenReturn(List.of(supplier));
        var controller = new CatalogLookupController(categoryService, supplierService);

        assertThat(controller.categories("tool", true).getBody().data())
                .containsExactly(new CatalogReferenceResponseDto(1L, "Tools"));
        assertThat(controller.suppliers("acme", true).getBody().data())
                .containsExactly(new CatalogReferenceResponseDto(2L, "Acme"));
        verify(categoryService).search(new CategoryFilter("tool", true));
        verify(supplierService).search(new SupplierFilter("acme", true));
    }
}
