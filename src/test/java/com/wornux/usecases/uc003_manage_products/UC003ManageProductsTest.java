package com.wornux.usecases.uc003_manage_products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryRepository;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductException;
import com.wornux.catalog.ProductFilter;
import com.wornux.catalog.ProductRepository;
import com.wornux.catalog.ProductRequest;
import com.wornux.catalog.ProductService;
import com.wornux.catalog.StockMovement;
import com.wornux.catalog.StockMovementRepository;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierRepository;
import com.wornux.ui.views.ProductsView;
import com.wornux.usecases.PostgresContainerConfig;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC003ManageProductsTest {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;

    private Category category;
    private Supplier supplier;

    @Autowired
    UC003ManageProductsTest(
            ProductService productService,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            StockMovementRepository stockMovementRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @BeforeEach
    void cleanProducts() {
        stockMovementRepository.deleteAll();
        productRepository.deleteAll();
        category = categoryRepository.findByActiveTrueOrderByNameAsc().getFirst();
        supplier = supplierRepository.findByActiveTrueOrderByNameAsc().getFirst();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void mainFlow_viewSearchFilterCreateEditAndDeleteProducts() {
        Product created = productService.create(request("sku-100", "Widget", 10, 4, 5));
        productService.create(request("sku-200", "Bracket", 20, 12, 4));

        assertThat(productService.search(new ProductFilter("wid", null, null, true, false)))
                .extracting(Product::getSku)
                .containsExactly("SKU-100");
        assertThat(productService.search(new ProductFilter("", category.getId(), supplier.getId(), true, true)))
                .extracting(Product::getSku)
                .containsExactly("SKU-100");

        ProductRequest update = request("sku-101", "Widget Pro", 15, 8, 4);
        update.setVersion(created.getVersion());
        productService.update(created.getId(), update);

        assertThat(productService.get(created.getId()).getName()).isEqualTo("Widget Pro");

        productService.delete(created.getId());

        assertThat(productRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af1_duplicateSkuIsRejected() {
        productService.create(request("dup-1", "First", 10, 8, 3));

        assertThatThrownBy(() -> productService.create(request("DUP-1", "Second", 12, 8, 3)))
                .isInstanceOf(ProductException.class)
                .hasMessage("SKU already exists. Please choose a different one.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af2_missingRequiredFieldsAreRejected() {
        ProductRequest request = request("", "", 10, 1, 1);
        request.setCategoryId(null);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("SKU is required.")
                .hasMessageContaining("Product name is required.")
                .hasMessageContaining("Category is required.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af3_invalidUnitPriceIsRejected() {
        assertThatThrownBy(() -> productService.create(request("bad-price", "Bad Price", -1, 1, 1)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Unit price must be a positive number.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_VIEWER")
    void af4_insufficientPermissionsCanOnlyReadProducts() {
        assertThat(productService.canManageProducts()).isFalse();

        assertThatThrownBy(() -> productService.create(request("viewer-create", "Viewer Create", 10, 2, 1)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("PRODUCT:CREATE/UPDATE/DELETE permission is required.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af5_productWithStockMovementsIsDeactivatedInsteadOfDeleted() {
        Product product = productService.create(request("history-1", "History Product", 10, 3, 2));
        stockMovementRepository.save(new StockMovement(product, null, "INITIAL_STOCK", 3, null));

        productService.delete(product.getId());

        Product reloaded = productService.get(product.getId());
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af6_unavailableCategoryOrSupplierIsRejected() {
        ProductRequest request = request("missing-category", "Missing Category", 10, 1, 1);
        request.setCategoryId(999_999L);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ProductException.class)
                .hasMessage("Selected category/supplier is no longer available. Please refresh and try again.");
    }

    @Test
    void af7_sidebarFormDirtyStateIsOwnedByTheProductsView() throws NoSuchFieldException {
        assertThat(ProductsView.class.getDeclaredField("dirty")).isNotNull();
        assertThat(ProductsView.class.getDeclaredField("dirtyDialog")).isNotNull();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af8_concurrentEditConflictIsRejected() {
        Product product = productService.create(request("conflict-1", "Conflict Product", 10, 4, 2));
        ProductRequest staleUpdate = request("conflict-1", "Conflict Product Updated", 12, 4, 2);
        staleUpdate.setVersion(product.getVersion() + 1);

        assertThatThrownBy(() -> productService.update(product.getId(), staleUpdate))
                .isInstanceOf(ProductException.class)
                .hasMessage("Product was updated by another user. Refresh the form and try again.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br01_skuMustBeUniqueAndNotBlank() {
        af1_duplicateSkuIsRejected();
        assertThatThrownBy(() -> productService.create(request(" ", "Blank SKU", 10, 1, 1)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br02_productNameMustBeUniqueWithinActiveProductsAndNotBlank() {
        productService.create(request("name-1", "Unique Name", 10, 1, 1));

        assertThatThrownBy(() -> productService.create(request("name-2", "unique name", 10, 1, 1)))
                .isInstanceOf(ProductException.class)
                .hasMessage("Product name already exists for an active product.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br03_unitPriceMustBeZeroOrGreater() {
        af3_invalidUnitPriceIsRejected();

        Product product = productService.create(request("free-1", "Free Sample", 0, 1, 1));
        assertThat(product.getUnitPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br04_quantityOnHandMustBeZeroOrGreater() {
        assertThatThrownBy(() -> productService.create(request("bad-qty", "Bad Quantity", 10, -1, 1)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Quantity on hand must be zero or greater.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br05_minimumStockMustBeZeroOrGreater() {
        assertThatThrownBy(() -> productService.create(request("bad-min", "Bad Minimum", 10, 1, -1)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Minimum stock must be zero or greater.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br06_categoryIsRequiredAndSupplierIsOptional() {
        ProductRequest noSupplier = request("no-supplier", "No Supplier", 10, 2, 1);
        noSupplier.setSupplierId(null);

        Product product = productService.create(noSupplier);

        assertThat(product.getCategory()).isNotNull();
        assertThat(product.getSupplier()).isNull();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br07_stockStatusIsLowStockWhenQuantityIsAtOrBelowMinimum() {
        Product low = productService.create(request("low-1", "Low Product", 10, 5, 5));
        Product ok = productService.create(request("ok-1", "Ok Product", 10, 6, 5));

        assertThat(low.isLowStock()).isTrue();
        assertThat(low.getStockStatus()).isEqualTo("LOW STOCK");
        assertThat(ok.isLowStock()).isFalse();
        assertThat(ok.getStockStatus()).isEqualTo("OK");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br08_productsWithHistoricalStockMovementsAreDeactivatedInsteadOfDeleted() {
        af5_productWithStockMovementsIsDeactivatedInsteadOfDeleted();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br09_searchFiltersBySkuAndNameCaseInsensitivePartialMatch() {
        productService.create(request("case-1", "Needle Product", 10, 2, 1));
        productService.create(request("case-2", "Other Product", 10, 2, 1));

        assertThat(productService.search(new ProductFilter("needle", null, null, true, false)))
                .extracting(Product::getSku)
                .containsExactly("CASE-1");
        assertThat(productService.search(new ProductFilter("ASE-2", null, null, true, false)))
                .extracting(Product::getName)
                .containsExactly("Other Product");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br10_lowStockFilterShowsProductsAtOrBelowMinimum() {
        productService.create(request("low-filter", "Low Filter", 10, 2, 2));
        productService.create(request("ok-filter", "Ok Filter", 10, 3, 2));

        assertThat(productService.search(new ProductFilter("", null, null, true, true)))
                .extracting(Product::getSku)
                .containsExactly("LOW-FILTER");
    }

    private ProductRequest request(String sku, String name, int unitPrice, int quantity, int minimumStock) {
        var request = new ProductRequest();
        request.setSku(sku);
        request.setName(name);
        request.setDescription(name + " description");
        request.setUnitPrice(BigDecimal.valueOf(unitPrice));
        request.setQuantityOnHand(quantity);
        request.setMinimumStock(minimumStock);
        request.setCategoryId(category.getId());
        request.setSupplierId(supplier.getId());
        request.setActive(true);
        return request;
    }
}
