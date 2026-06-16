package com.wornux.usecases.uc004_manage_stock_movements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wornux.catalog.Category;
import com.wornux.catalog.CategoryRepository;
import com.wornux.catalog.MovementType;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductRepository;
import com.wornux.catalog.ProductRequest;
import com.wornux.catalog.ProductService;
import com.wornux.catalog.StockMovement;
import com.wornux.catalog.StockMovementException;
import com.wornux.catalog.StockMovementFilter;
import com.wornux.catalog.StockMovementRepository;
import com.wornux.catalog.StockMovementRequest;
import com.wornux.catalog.StockMovementService;
import com.wornux.catalog.Supplier;
import com.wornux.catalog.SupplierRepository;
import com.wornux.ui.views.StockMovementsView;
import com.wornux.usecases.PostgresContainerConfig;
import com.wornux.user.AppUserRepository;
import com.wornux.user.AppUserService;
import com.wornux.user.SignupRequest;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@Import(PostgresContainerConfig.class)
class UC004ManageStockMovementsTest {

    private final StockMovementService stockMovementService;
    private final StockMovementRepository stockMovementRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final AppUserService appUserService;
    private final AppUserRepository appUserRepository;

    private Category category;
    private Supplier supplier;

    @Autowired
    UC004ManageStockMovementsTest(
            StockMovementService stockMovementService,
            StockMovementRepository stockMovementRepository,
            ProductService productService,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            AppUserService appUserService,
            AppUserRepository appUserRepository) {
        this.stockMovementService = stockMovementService;
        this.stockMovementRepository = stockMovementRepository;
        this.productService = productService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.appUserService = appUserService;
        this.appUserRepository = appUserRepository;
    }

    @BeforeEach
    void cleanData() {
        stockMovementRepository.deleteAll();
        productRepository.deleteAll();
        appUserRepository.deleteAll();
        category = categoryRepository.findByActiveTrueOrderByNameAsc().getFirst();
        supplier = supplierRepository.findByActiveTrueOrderByNameAsc().getFirst();
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void mainFlow_viewFilterAndCreateStockMovement() {
        signupUser("manager");
        Product product = productService.create(productRequest("stock-100", "Ledger Product", 5));

        StockMovement created = stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.PURCHASE, 3, null));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(8);
        assertThat(created.getMovementType()).isEqualTo(MovementType.PURCHASE);

        assertThat(stockMovementService.search(new StockMovementFilter(
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                product.getId(),
                MovementType.PURCHASE,
                "manager")))
                .extracting(StockMovement::getId)
                .containsExactly(created.getId());
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void af1_missingRequiredFieldsAreRejected() {
        signupUser("manager");

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(new StockMovementRequest()))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("Product is required.")
                .hasMessageContaining("Movement type is required.")
                .hasMessageContaining("Quantity delta is required.");
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void af2_invalidQuantityDeltaIsRejected() {
        signupUser("manager");
        Product product = productService.create(productRequest("delta-100", "Delta Product", 5));

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.PURCHASE, -1, null)))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must be positive for this movement type.");

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.SALE, 1, null)))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must be negative for this movement type.");
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void af3_insufficientStockIsRejected() {
        signupUser("manager");
        Product product = productService.create(productRequest("short-100", "Short Product", 1));

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.SALE, -2, null)))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Insufficient stock. Current stock: 1, requested: 2.");
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void af4_inactiveProductIsRejected() {
        signupUser("manager");
        Product product = productService.create(productRequest("inactive-100", "Inactive Product", 5));
        product.deactivate();
        productRepository.save(product);

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.PURCHASE, 1, null)))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Product is no longer available.");
    }

    @Test
    @WithMockUser(username = "viewer", roles = "INVENTORY_VIEWER")
    void af5_insufficientPermissionsCanOnlyReadMovements() {
        signupUser("viewer");
        assertThat(stockMovementService.canCreateMovements()).isFalse();

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                1L, MovementType.PURCHASE, 1, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("STOCK_MOVEMENT:CREATE permission is required.");
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void af6_databaseErrorRollsBackProductQuantityAndMovementInsert() {
        signupUser("manager");
        Product product = productService.create(productRequest("db-100", "Database Product", 5));

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.PURCHASE, 3, "x".repeat(501))))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Failed to save movement. Please try again.");

        assertThat(productRepository.findById(product.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(5);
        assertThat(stockMovementRepository.findAll()).isEmpty();
    }

    @Test
    void af7_sidebarFormDirtyStateIsOwnedByTheStockMovementsView() throws NoSuchFieldException {
        assertThat(StockMovementsView.class.getDeclaredField("dirty")).isNotNull();
        assertThat(StockMovementsView.class.getDeclaredField("dirtyDialog")).isNotNull();
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void af8_noProductsAvailableLeavesCreateFormWithoutProductChoices() {
        signupUser("manager");

        assertThat(stockMovementService.activeProducts()).isEmpty();
    }

    @Test
    void br01AndBr11_stockMovementsAreAppendOnlyInTheServiceAndView() {
        assertThat(Arrays.stream(StockMovementService.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain("update", "delete");
        assertThat(Arrays.stream(StockMovementsView.class.getDeclaredFields())
                .map(field -> field.getName()))
                .doesNotContain("deleteDialog");
    }

    @Test
    void br02_movementTypeUsesOnlyApprovedEnumValues() {
        assertThat(MovementType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "PURCHASE",
                        "SALE",
                        "RETURN_IN",
                        "RETURN_OUT",
                        "ADJUSTMENT_IN",
                        "ADJUSTMENT_OUT",
                        "INITIAL_STOCK",
                        "DAMAGED",
                        "LOST");
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void br03_quantityDeltaMustNotBeZero() {
        signupUser("manager");
        Product product = productService.create(productRequest("zero-100", "Zero Product", 5));

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.PURCHASE, 0, null)))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must not be zero.");
    }

    @Test
    void br04AndBr05_movementTypeDeclaresRequiredSign() {
        assertThat(MovementType.PURCHASE.isPositive()).isTrue();
        assertThat(MovementType.RETURN_IN.isPositive()).isTrue();
        assertThat(MovementType.ADJUSTMENT_IN.isPositive()).isTrue();
        assertThat(MovementType.INITIAL_STOCK.isPositive()).isTrue();
        assertThat(MovementType.SALE.isNegative()).isTrue();
        assertThat(MovementType.RETURN_OUT.isNegative()).isTrue();
        assertThat(MovementType.ADJUSTMENT_OUT.isNegative()).isTrue();
        assertThat(MovementType.DAMAGED.isNegative()).isTrue();
        assertThat(MovementType.LOST.isNegative()).isTrue();
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void br06AndBr07_reasonRulesMatchMovementType() {
        signupUser("manager");
        Product product = productService.create(productRequest("reason-100", "Reason Product", 5));

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.DAMAGED, -1, " ")))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Reason is required for this movement type.");

        StockMovement sale = stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.SALE, -1, null));
        assertThat(sale.getReason()).isNull();
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void br08AndBr09_outboundMovementsDoNotGoBelowZeroAndSuccessfulMovementIsAtomic() {
        signupUser("manager");
        Product product = productService.create(productRequest("atomic-100", "Atomic Product", 2));

        assertThatThrownBy(() -> stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.LOST, -3, "Count mismatch")))
                .isInstanceOf(StockMovementException.class);

        StockMovement movement = stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.LOST, -1, "Count mismatch"));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getQuantityOnHand()).isEqualTo(1);
        assertThat(stockMovementRepository.findById(movement.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = "manager", roles = "INVENTORY_MANAGER")
    void br10_movementRecordsTimestampUserAndQuantityDelta() {
        signupUser("manager");
        Product product = productService.create(productRequest("audit-100", "Audit Product", 5));

        StockMovement movement = stockMovementService.recordStockMovement(movementRequest(
                product.getId(), MovementType.RETURN_OUT, -2, null));
        StockMovement reloaded = stockMovementService.search(new StockMovementFilter(
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60),
                product.getId(),
                MovementType.RETURN_OUT,
                "manager")).getFirst();

        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUser()).isNotNull();
        assertThat(reloaded.getUser().getUsername()).isEqualTo("manager");
        assertThat(reloaded.getQuantityDelta()).isEqualTo(-2);
    }

    private ProductRequest productRequest(String sku, String name, int quantity) {
        var request = new ProductRequest();
        request.setSku(sku);
        request.setName(name);
        request.setDescription(name + " description");
        request.setUnitPrice(BigDecimal.TEN);
        request.setQuantityOnHand(quantity);
        request.setMinimumStock(1);
        request.setCategoryId(category.getId());
        request.setSupplierId(supplier.getId());
        request.setActive(true);
        return request;
    }

    private StockMovementRequest movementRequest(
            Long productId, MovementType movementType, Integer quantityDelta, String reason) {
        var request = new StockMovementRequest();
        request.setProductId(productId);
        request.setMovementType(movementType);
        request.setQuantityDelta(quantityDelta);
        request.setReason(reason);
        return request;
    }

    private void signupUser(String username) {
        var request = new SignupRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.test");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        appUserService.signup(request);
    }
}
