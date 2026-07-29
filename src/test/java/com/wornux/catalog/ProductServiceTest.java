package com.wornux.catalog;

import static com.wornux.SpecificationTestSupport.predicateCount;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock
    ProductRepository products;

    @Mock
    CategoryRepository categories;

    @Mock
    SupplierRepository suppliers;

    @Mock
    StockMovementRepository movements;

    @Mock
    AuthorizationService authorization;

    ProductService service;
    Category category;
    Supplier supplier;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, categories, suppliers, movements, authorization);
        category = new Category("Tools", null);
        supplier = new Supplier("Acme", null, null, null);
    }

    @Test
    void searchesNormalizeNullAndExplicitFiltersForPages() {
        ProductFilter filter = new ProductFilter(" HAMMER ", 1L, 2L, true, true);
        var pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(product(true)));
        when(products.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        assertThat(service.search(null, pageable)).isSameAs(page);
        assertThat(service.search(filter, pageable)).isSameAs(page);
        assertThat(service.search(new ProductFilter(null, null, null, null, false), pageable))
                .isSameAs(page);
        ArgumentCaptor<Specification<Product>> specifications = ArgumentCaptor.captor();
        verify(products, times(3)).findAll(specifications.capture(), eq(pageable));
        assertThat(specifications.getAllValues())
                .extracting(specification -> predicateCount(specification))
                .containsExactly(0, 5, 0);
    }

    @Test
    void getAndLookupListsHaveClearMissingSemantics() {
        Product p = product(true);
        when(products.findWithCategoryAndSupplierById(1L)).thenReturn(Optional.of(p));

        assertThat(service.get(1L)).isSameAs(p);
        assertThatThrownBy(() -> service.get(2L)).hasMessage("Product was not found.");

        when(categories.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(category));
        when(suppliers.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(supplier));

        assertThat(service.activeCategories()).containsExactly(category);
        assertThat(service.activeSuppliers()).containsExactly(supplier);
    }

    @Test
    void createNormalizesFieldsAndAllowsNoSupplier() {
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(products.save(any())).thenAnswer(i -> i.getArgument(0));

        Product p = service.create(request(" sku-a ", " Hammer ", " ", null, true));

        assertThat(p.getSku()).isEqualTo("SKU-A");
        assertThat(p.getName()).isEqualTo("Hammer");
        assertThat(p.getDescription()).isNull();
        assertThat(p.getSupplier()).isNull();

        when(suppliers.findById(2L)).thenReturn(Optional.of(supplier));

        Product supplied = service.create(request("sku-b", "Mallet", " hardwood ", 2L, true));

        assertThat(supplied.getSupplier()).isSameAs(supplier);
        assertThat(supplied.getDescription()).isEqualTo("hardwood");
    }

    @Test
    void createRejectsDuplicateSkuNameAndUnavailableRelationships() {
        ProductRequest r = request("sku", "Name", null, 2L, true);
        when(products.existsBySkuIgnoreCase("SKU")).thenReturn(true);

        assertThatThrownBy(() -> service.create(r)).hasMessageContaining("SKU already");

        when(products.existsBySkuIgnoreCase("SKU")).thenReturn(false);
        when(products.existsActiveNameExcludingId("Name", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(r)).hasMessageContaining("name already");

        when(products.existsActiveNameExcludingId("Name", null)).thenReturn(false);

        assertThatThrownBy(() -> service.create(r)).hasMessageContaining("no longer available");

        Category inactive = new Category("Old", null);
        inactive.deactivate();
        when(categories.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(r)).hasMessageContaining("no longer available");

        when(categories.findById(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.create(r)).hasMessageContaining("no longer available");

        Supplier inactiveSupplier = new Supplier("Old", null, null, null);
        inactiveSupplier.deactivate();
        when(suppliers.findById(2L)).thenReturn(Optional.of(inactiveSupplier));

        assertThatThrownBy(() -> service.create(r)).hasMessageContaining("no longer available");
    }

    @Test
    void updateEnforcesVersionUniquenessAndChangesRelationships() {
        Product p = product(true);
        when(products.findWithCategoryAndSupplierById(1L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.update(1L, request("x", "X", null, null, true, 1L)))
                .hasMessageContaining("another user");
        assertThatThrownBy(() -> service.update(2L, request("x", "X", null, null, true)))
                .hasMessage("Product was not found.");
        when(products.existsBySkuIgnoreCaseAndIdNot("X", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("x", "X", null, null, true)))
                .hasMessageContaining("SKU already");
        when(products.existsBySkuIgnoreCaseAndIdNot("X", 1L)).thenReturn(false);
        when(products.existsActiveNameExcludingId("X", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("x", "X", null, null, true)))
                .hasMessageContaining("name already");
        when(products.existsActiveNameExcludingId("", 1L)).thenReturn(false);
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(products.save(p)).thenReturn(p);

        assertThat(service.update(1L, request(null, null, null, null, false))).isSameAs(p);
        assertThat(p.getSku()).isEmpty();
        assertThat(p.getName()).isEmpty();
        assertThat(p.isActive()).isFalse();
    }

    @Test
    void deleteHardDeletesWithoutLedgerAndSoftDeletesWithLedger() {
        Product p = product(true);
        when(products.findWithCategoryAndSupplierById(1L)).thenReturn(Optional.of(p));

        service.delete(1L);

        verify(products).delete(p);

        Product linked = product(true);
        when(products.findWithCategoryAndSupplierById(2L)).thenReturn(Optional.of(linked));
        when(movements.existsByProductId(2L)).thenReturn(true);

        service.delete(2L);

        assertThat(linked.isActive()).isFalse();
        verify(products).save(linked);
        verify(products, never()).delete(linked);
        assertThatThrownBy(() -> service.delete(3L)).hasMessage("Product was not found.");
    }

    @Test
    void capabilitiesDelegateExactPermissions() {
        when(authorization.can(AppPermission.PRODUCT_CREATE)).thenReturn(true);
        when(authorization.can(AppPermission.PRODUCT_UPDATE)).thenReturn(false);
        when(authorization.can(AppPermission.PRODUCT_DELETE)).thenReturn(true);

        assertThat(service.canCreateProducts()).isTrue();
        assertThat(service.canUpdateProducts()).isFalse();
        assertThat(service.canDeleteProducts()).isTrue();
    }

    private Product product(boolean active) {
        return new Product("SKU", "Name", null, BigDecimal.TEN, 4, 2, category, supplier, active);
    }

    private static ProductRequest request(String sku, String name, String desc, Long supplier, boolean active) {
        return request(sku, name, desc, supplier, active, null);
    }

    private static ProductRequest request(
            String sku, String name, String desc, Long supplier, boolean active, Long version) {
        ProductRequest r = new ProductRequest();
        r.setSku(sku);
        r.setName(name);
        r.setDescription(desc);
        r.setUnitPrice(BigDecimal.TEN);
        r.setQuantityOnHand(4);
        r.setMinimumStock(2);
        r.setCategoryId(1L);
        r.setSupplierId(supplier);
        r.setActive(active);
        r.setVersion(version);

        return r;
    }
}
