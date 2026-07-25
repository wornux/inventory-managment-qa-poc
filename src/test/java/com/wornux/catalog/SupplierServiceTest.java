package com.wornux.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {
    @Mock SupplierRepository suppliers; @Mock ProductRepository products; @Mock AuthorizationService authorization;
    SupplierService service;
    @BeforeEach void setUp() { service = new SupplierService(suppliers, products, authorization); }

    @Test void readOperationsNormalizeAndPreserveRepositoryResults() {
        Supplier supplier = new Supplier("Acme", null, null, null);
        Supplier inactive = new Supplier("Archived", null, null, null);
        when(suppliers.search("", null)).thenReturn(List.of());
        when(suppliers.search("acme", true)).thenReturn(List.of(supplier));
        when(suppliers.search("", false)).thenReturn(List.of(inactive));
        assertThat(service.search(null)).isEmpty();
        assertThat(service.search(new SupplierFilter(" ACME ", true))).containsExactly(supplier);
        assertThat(service.search(new SupplierFilter(null, false))).containsExactly(inactive);
        when(suppliers.findById(1L)).thenReturn(Optional.of(supplier));
        assertThat(service.get(1L)).isSameAs(supplier);
        assertThatThrownBy(() -> service.get(2L)).hasMessage("Supplier was not found.");
        when(products.countBySupplierId(1L)).thenReturn(5L); when(products.countBySupplierIdAndActiveTrue(1L)).thenReturn(3L);
        assertThat(service.productCount(1L)).isEqualTo(5); assertThat(service.activeProductCount(1L)).isEqualTo(3);
    }

    @Test void createTrimsOptionalContactDataToNull() {
        when(suppliers.save(any())).thenAnswer(i -> i.getArgument(0));
        Supplier created = service.create(request(null, " ", null, "  ", false, null));
        assertThat(created.getName()).isEmpty();
        assertThat(created.getContactName()).isNull(); assertThat(created.getEmail()).isNull(); assertThat(created.getPhone()).isNull();
        assertThat(created.isActive()).isFalse();
    }

    @Test void updateEnforcesOptimisticVersionThenUpdatesAllBusinessFields() {
        Supplier supplier = new Supplier("Old", null, null, null);
        when(suppliers.findById(1L)).thenReturn(Optional.of(supplier));
        assertThatThrownBy(() -> service.update(1L, request("New", null, null, null, true, 1L))).hasMessageContaining("another user");
        assertThatThrownBy(() -> service.update(2L, request("New", null, null, null, true, null))).hasMessage("Supplier was not found.");
        when(suppliers.save(supplier)).thenReturn(supplier);
        Supplier result = service.update(1L, request(" New ", " Joe ", " a@b.com ", " 123 ", false, null));
        assertThat(result.getName()).isEqualTo("New"); assertThat(result.getContactName()).isEqualTo("Joe");
        assertThat(result.getEmail()).isEqualTo("a@b.com"); assertThat(result.getPhone()).isEqualTo("123"); assertThat(result.isActive()).isFalse();
    }

    @Test void deactivateAndCapabilitiesApplySupplierPermissions() {
        Supplier supplier = new Supplier("Acme", null, null, null); when(suppliers.findById(1L)).thenReturn(Optional.of(supplier));
        service.deactivate(1L); assertThat(supplier.isActive()).isFalse();
        assertThatThrownBy(() -> service.deactivate(2L)).hasMessage("Supplier was not found.");
        when(authorization.can(AppPermission.SUPPLIER_CREATE)).thenReturn(true); when(authorization.can(AppPermission.SUPPLIER_UPDATE)).thenReturn(false); when(authorization.can(AppPermission.SUPPLIER_DELETE)).thenReturn(true);
        assertThat(service.canCreateSuppliers()).isTrue(); assertThat(service.canUpdateSuppliers()).isFalse(); assertThat(service.canDeleteSuppliers()).isTrue();
    }

    private static SupplierRequest request(String n,String c,String e,String p,boolean a,Long v) { SupplierRequest r=new SupplierRequest();r.setName(n);r.setContactName(c);r.setEmail(e);r.setPhone(p);r.setActive(a);r.setVersion(v);return r; }
}
