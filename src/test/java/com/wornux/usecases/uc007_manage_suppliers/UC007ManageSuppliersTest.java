package com.wornux.usecases.uc007_manage_suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryRepository;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductException;
import com.wornux.catalog.ProductRepository;
import com.wornux.catalog.ProductRequest;
import com.wornux.catalog.ProductService;
import com.wornux.catalog.StockMovementRepository;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierException;
import com.wornux.catalog.SupplierFilter;
import com.wornux.catalog.SupplierRequest;
import com.wornux.catalog.SupplierService;
import com.wornux.ui.views.SuppliersView;
import com.wornux.usecases.PostgresContainerConfig;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC007ManageSuppliersTest {

    private final SupplierService supplierService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockMovementRepository stockMovementRepository;

    private Category category;

    @Autowired
    UC007ManageSuppliersTest(
            SupplierService supplierService,
            ProductService productService,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            StockMovementRepository stockMovementRepository) {
        this.supplierService = supplierService;
        this.productService = productService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @BeforeEach
    void cleanProducts() {
        stockMovementRepository.deleteAll();
        productRepository.deleteAll();
        category = categoryRepository.findByActiveTrueOrderByNameAsc().getFirst();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void mainFlow_viewSearchCreateEditAndDeactivateSuppliers() {
        Supplier created = supplierService.create(request(unique("Acme distributor"), "Ada Buyer", "ada@example.com", "+1 555-0100", true));

        assertThat(supplierService.search(new SupplierFilter("ada buyer", true)))
                .extracting(Supplier::getName)
                .containsExactly(created.getName());

        SupplierRequest update = request(unique("Acme distributor updated"), "Grace Buyer", "grace@example.com", "(555) 0101", true);
        update.setVersion(created.getVersion());
        supplierService.update(created.getId(), update);

        Supplier updated = supplierService.get(created.getId());
        assertThat(updated.getContactName()).isEqualTo("Grace Buyer");
        assertThat(updated.getEmail()).isEqualTo("grace@example.com");

        supplierService.deactivate(updated.getId());

        assertThat(supplierService.search(new SupplierFilter(updated.getName(), false)))
                .extracting(Supplier::getId)
                .containsExactly(updated.getId());
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af1_missingRequiredFieldsAreRejected() {
        assertThatThrownBy(() -> supplierService.create(request("", null, null, null, true)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Supplier name is required.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af2_invalidEmailFormatIsRejected() {
        assertThatThrownBy(() -> supplierService.create(request(unique("Bad email"), null, "not-an-email", null, true)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Invalid email address.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af3_invalidPhoneFormatIsRejected() {
        assertThatThrownBy(() -> supplierService.create(request(unique("Bad phone"), null, null, "555-ABCD", true)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Invalid phone number format.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_VIEWER")
    void af4_insufficientPermissionsCanOnlyReadSuppliers() {
        assertThat(supplierService.canManageSuppliers()).isFalse();
        assertThat(supplierService.search(new SupplierFilter("", true))).isNotEmpty();

        assertThatThrownBy(() -> supplierService.create(request(unique("Viewer blocked"), null, null, null, true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("SUPPLIER:CREATE/UPDATE/DELETE permission is required.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af5_supplierWithProductsCanBeDeactivatedWithoutAffectingExistingProducts() {
        Supplier supplier = supplierService.create(request(unique("Products supplier"), null, null, null, true));
        Product product = productService.create(productRequest("supplier-product", "Supplier Product", supplier));

        assertThat(supplierService.activeProductCount(supplier.getId())).isEqualTo(1);

        supplierService.deactivate(supplier.getId());

        assertThat(productService.get(product.getId()).isActive()).isTrue();
        assertThat(supplierService.get(supplier.getId()).isActive()).isFalse();
    }

    @Test
    void af6_sidebarFormDirtyStateIsOwnedBySuppliersView() throws NoSuchFieldException {
        assertThat(SuppliersView.class.getDeclaredField("dirty")).isNotNull();
        assertThat(SuppliersView.class.getDeclaredField("dirtyDialog")).isNotNull();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af7_concurrentEditConflictIsRejected() {
        Supplier supplier = supplierService.create(request(unique("Conflict supplier"), null, null, null, true));
        SupplierRequest stale = request(unique("Conflict supplier updated"), null, null, null, true);
        stale.setVersion(supplier.getVersion() + 1);

        assertThatThrownBy(() -> supplierService.update(supplier.getId(), stale))
                .isInstanceOf(SupplierException.class)
                .hasMessage("Supplier was updated by another user. Refresh the form and try again.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br01_supplierNameMustNotBeBlank() {
        af1_missingRequiredFieldsAreRejected();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br02_contactFieldsAreOptional() {
        Supplier supplier = supplierService.create(request(unique("Optional contact"), null, null, null, true));

        assertThat(supplier.getContactName()).isNull();
        assertThat(supplier.getEmail()).isNull();
        assertThat(supplier.getPhone()).isNull();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br03AndBr04_emailAndPhoneFormatsAreValidated() {
        af2_invalidEmailFormatIsRejected();
        af3_invalidPhoneFormatIsRejected();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br05AndBr06_suppliersAreDeactivatedWithoutRemovingProductHistory() {
        af5_supplierWithProductsCanBeDeactivatedWithoutAffectingExistingProducts();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br07_newProductsCannotUseInactiveSuppliers() {
        Supplier supplier = supplierService.create(request(unique("Inactive assignment"), null, null, null, true));
        supplierService.deactivate(supplier.getId());

        assertThatThrownBy(() -> productService.create(productRequest("inactive-supplier", "Inactive Supplier Product", supplier)))
                .isInstanceOf(ProductException.class)
                .hasMessage("Selected category/supplier is no longer available. Please refresh and try again.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br08_searchFiltersBySupplierNameAndContactNameCaseInsensitivePartialMatch() {
        Supplier needle = supplierService.create(request(unique("Needle supplier"), "Find Me", null, null, true));
        supplierService.create(request(unique("Other supplier"), "Ignore Me", null, null, true));

        assertThat(supplierService.search(new SupplierFilter("find me", true)))
                .extracting(Supplier::getId)
                .containsExactly(needle.getId());
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br09_gridDisplaysProductCountForEachSupplier() {
        Supplier supplier = supplierService.create(request(unique("Counting supplier"), null, null, null, true));
        productService.create(productRequest("count-a", "Count A", supplier));
        productService.create(productRequest("count-b", "Count B", supplier));

        assertThat(supplierService.productCount(supplier.getId())).isEqualTo(2);
    }

    private SupplierRequest request(String name, String contactName, String email, String phone, boolean active) {
        var request = new SupplierRequest();
        request.setName(name);
        request.setContactName(contactName);
        request.setEmail(email);
        request.setPhone(phone);
        request.setActive(active);
        return request;
    }

    private ProductRequest productRequest(String skuSeed, String nameSeed, Supplier supplier) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var request = new ProductRequest();
        request.setSku((skuSeed + "-" + suffix).toUpperCase());
        request.setName(nameSeed + " " + suffix);
        request.setDescription(nameSeed + " description");
        request.setUnitPrice(BigDecimal.TEN);
        request.setQuantityOnHand(3);
        request.setMinimumStock(1);
        request.setCategoryId(category.getId());
        request.setSupplierId(supplier.getId());
        request.setActive(true);
        return request;
    }

    private String unique(String value) {
        return value + " " + UUID.randomUUID().toString().substring(0, 8);
    }
}
