package com.wornux.usecases.uc006_manage_categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryException;
import com.wornux.catalog.CategoryFilter;
import com.wornux.catalog.CategoryRequest;
import com.wornux.catalog.CategoryService;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductException;
import com.wornux.catalog.ProductFilter;
import com.wornux.catalog.ProductRepository;
import com.wornux.catalog.ProductRequest;
import com.wornux.catalog.ProductService;
import com.wornux.catalog.StockMovementRepository;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierRepository;
import com.wornux.ui.views.CategoriesView;
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
class UC006ManageCategoriesTest {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;

    private Supplier supplier;

    @Autowired
    UC006ManageCategoriesTest(
            CategoryService categoryService,
            ProductService productService,
            ProductRepository productRepository,
            SupplierRepository supplierRepository,
            StockMovementRepository stockMovementRepository) {
        this.categoryService = categoryService;
        this.productService = productService;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @BeforeEach
    void cleanProducts() {
        stockMovementRepository.deleteAll();
        productRepository.deleteAll();
        supplier = supplierRepository.findByActiveTrueOrderByNameAsc().getFirst();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void mainFlow_viewSearchCreateEditAndDeactivateCategories() {
        Category created = categoryService.create(request(unique("Hardware bins"), "Storage category", true));

        assertThat(categoryService.search(new CategoryFilter("hardware bins", true)))
                .extracting(Category::getName)
                .containsExactly(created.getName());

        CategoryRequest update = request(unique("Hardware bins updated"), "Updated description", true);
        update.setVersion(created.getVersion());
        categoryService.update(created.getId(), update);

        Category updated = categoryService.get(created.getId());
        assertThat(updated.getDescription()).isEqualTo("Updated description");

        categoryService.deactivate(updated.getId());

        assertThat(categoryService.search(new CategoryFilter(updated.getName(), false)))
                .extracting(Category::getId)
                .containsExactly(updated.getId());
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af1_duplicateCategoryNameIsRejected() {
        Category category = categoryService.create(request(unique("Duplicate category"), null, true));

        assertThatThrownBy(() -> categoryService.create(request(category.getName().toUpperCase(), null, true)))
                .isInstanceOf(CategoryException.class)
                .hasMessage("Category name already exists. Please choose a different one.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af2_missingRequiredFieldsAreRejected() {
        assertThatThrownBy(() -> categoryService.create(request("", null, true)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Category name is required.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_VIEWER")
    void af3_insufficientPermissionsCanOnlyReadCategories() {
        assertThat(categoryService.canManageCategories()).isFalse();
        assertThat(categoryService.search(new CategoryFilter("", true))).isNotEmpty();

        assertThatThrownBy(() -> categoryService.create(request(unique("Viewer blocked"), null, true)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("CATEGORY:CREATE/UPDATE/DELETE permission is required.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af4_categoryWithProductsCanBeDeactivatedWithoutAffectingExistingProducts() {
        Category category = categoryService.create(request(unique("Products category"), null, true));
        Product product = productService.create(productRequest("cat-product", "Categorized Product", category));

        assertThat(categoryService.activeProductCount(category.getId())).isEqualTo(1);

        categoryService.deactivate(category.getId());

        assertThat(productService.get(product.getId()).isActive()).isTrue();
        assertThat(categoryService.get(category.getId()).isActive()).isFalse();
    }

    @Test
    void af5_sidebarFormDirtyStateIsOwnedByCategoriesView() throws NoSuchFieldException {
        assertThat(CategoriesView.class.getDeclaredField("dirty")).isNotNull();
        assertThat(CategoriesView.class.getDeclaredField("dirtyDialog")).isNotNull();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void af6_concurrentEditConflictIsRejected() {
        Category category = categoryService.create(request(unique("Conflict category"), null, true));
        CategoryRequest stale = request(unique("Conflict category updated"), null, true);
        stale.setVersion(category.getVersion() + 1);

        assertThatThrownBy(() -> categoryService.update(category.getId(), stale))
                .isInstanceOf(CategoryException.class)
                .hasMessage("Category was updated by another user. Refresh the form and try again.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br01_categoryNameMustBeUniqueAndNotBlank() {
        af1_duplicateCategoryNameIsRejected();
        af2_missingRequiredFieldsAreRejected();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br02_descriptionIsOptional() {
        Category category = categoryService.create(request(unique("No description"), null, true));

        assertThat(category.getDescription()).isNull();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br03AndBr04_categoriesAreDeactivatedWithoutRemovingProductHistory() {
        af4_categoryWithProductsCanBeDeactivatedWithoutAffectingExistingProducts();
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br05_newProductsCannotUseInactiveCategories() {
        Category category = categoryService.create(request(unique("Inactive assignment"), null, true));
        categoryService.deactivate(category.getId());

        assertThatThrownBy(() -> productService.create(productRequest("inactive-category", "Inactive Category Product", category)))
                .isInstanceOf(ProductException.class)
                .hasMessage("Selected category/supplier is no longer available. Please refresh and try again.");
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br06_searchFiltersByCategoryNameCaseInsensitivePartialMatch() {
        Category needle = categoryService.create(request(unique("Needle category"), null, true));
        categoryService.create(request(unique("Other category"), null, true));

        assertThat(categoryService.search(new CategoryFilter("needle", true)))
                .extracting(Category::getId)
                .containsExactly(needle.getId());
    }

    @Test
    @WithMockUser(roles = "INVENTORY_MANAGER")
    void br07_gridDisplaysProductCountForEachCategory() {
        Category category = categoryService.create(request(unique("Counting category"), null, true));
        productService.create(productRequest("count-a", "Count A", category));
        productService.create(productRequest("count-b", "Count B", category));

        assertThat(categoryService.productCount(category.getId())).isEqualTo(2);
    }

    private CategoryRequest request(String name, String description, boolean active) {
        var request = new CategoryRequest();
        request.setName(name);
        request.setDescription(description);
        request.setActive(active);
        return request;
    }

    private ProductRequest productRequest(String skuSeed, String nameSeed, Category category) {
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
