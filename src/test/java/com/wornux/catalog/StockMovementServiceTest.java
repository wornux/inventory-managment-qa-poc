package com.wornux.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    private static final Instant LEDGER_START = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant LEDGER_END = Instant.parse("9999-12-31T00:00:00Z");

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private StockMovementService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void search_withNullFilter_usesLedgerDefaultsAndEmptyUsername() {
        authenticate("viewer", "ROLE_INVENTORY_VIEWER");
        List<StockMovement> expected = List.of();
        when(stockMovementRepository.search(LEDGER_START, LEDGER_END, null, null, ""))
                .thenReturn(expected);

        List<StockMovement> result = service.search(null);

        assertThat(result).isSameAs(expected);
        verify(stockMovementRepository).search(LEDGER_START, LEDGER_END, null, null, "");
    }

    @Test
    void search_withFilter_trimsUsernameAndPassesFilterValues() {
        authenticate("manager", "ROLE_INVENTORY_MANAGER");
        Instant createdFrom = Instant.parse("2026-01-01T00:00:00Z");
        Instant createdTo = Instant.parse("2026-01-31T00:00:00Z");
        StockMovementFilter filter = new StockMovementFilter(createdFrom, createdTo, 9L, MovementType.SALE, " manager ");
        List<StockMovement> expected = List.of(movement(product(10), user("manager"), MovementType.SALE, -2, null));
        when(stockMovementRepository.search(createdFrom, createdTo, 9L, MovementType.SALE, "manager"))
                .thenReturn(expected);

        List<StockMovement> result = service.search(filter);

        assertThat(result).isSameAs(expected);
        verify(stockMovementRepository).search(createdFrom, createdTo, 9L, MovementType.SALE, "manager");
    }

    @Test
    void search_withoutReadRole_throwsAccessDeniedException() {
        authenticate("outsider", "ROLE_SALES_ASSISTANT");

        assertThatThrownBy(() -> service.search(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("STOCK_MOVEMENT:READ permission is required.");

        verifyNoInteractions(stockMovementRepository);
    }

    @Test
    void activeProducts_withReadRole_returnsActiveProducts() {
        authenticate("viewer", "ROLE_INVENTORY_VIEWER");
        List<Product> expected = List.of(product(4), product(7));
        when(productRepository.findByActiveTrueOrderBySkuAsc()).thenReturn(expected);

        List<Product> result = service.activeProducts();

        assertThat(result).isSameAs(expected);
        verify(productRepository).findByActiveTrueOrderBySkuAsc();
    }

    @Test
    void movementUsernames_withReadRole_returnsDistinctUsernames() {
        authenticate("operator", "ROLE_WAREHOUSE_OPERATOR");
        List<String> expected = List.of("admin", "operator");
        when(stockMovementRepository.findDistinctUsernames()).thenReturn(expected);

        List<String> result = service.movementUsernames();

        assertThat(result).isSameAs(expected);
        verify(stockMovementRepository).findDistinctUsernames();
    }

    @Test
    void canCreateMovements_allowsOperatorManagerAndAdmin() {
        authenticate("operator", "ROLE_WAREHOUSE_OPERATOR");
        assertThat(service.canCreateMovements()).isTrue();

        authenticate("manager", "ROLE_INVENTORY_MANAGER");
        assertThat(service.canCreateMovements()).isTrue();

        authenticate("admin", "ROLE_SYSTEM_ADMINISTRATOR");
        assertThat(service.canCreateMovements()).isTrue();
    }

    @Test
    void canCreateMovements_rejectsViewerAndAnonymous() {
        authenticate("viewer", "ROLE_INVENTORY_VIEWER");
        assertThat(service.canCreateMovements()).isFalse();

        SecurityContextHolder.clearContext();
        assertThat(service.canCreateMovements()).isFalse();
    }

    @Test
    void record_withPurchase_updatesProductStockAndSavesMovement() {
        authenticate("manager", "ROLE_INVENTORY_MANAGER");
        Product product = product(5);
        AppUser user = user("manager");
        when(productRepository.findWithCategoryAndSupplierById(12L)).thenReturn(Optional.of(product));
        when(appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(user));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockMovement result = service.recordStockMovement(request(12L, MovementType.PURCHASE, 3, "  restock  "));

        assertThat(product.getQuantityOnHand()).isEqualTo(8);
        assertThat(result.getProduct()).isSameAs(product);
        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getMovementType()).isEqualTo(MovementType.PURCHASE);
        assertThat(result.getQuantityDelta()).isEqualTo(3);
        assertThat(result.getReason()).isEqualTo("restock");
        verify(productRepository).save(product);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    void record_withSale_updatesProductStockAndSavesNegativeMovement() {
        authenticate("operator", "ROLE_WAREHOUSE_OPERATOR");
        Product product = product(5);
        AppUser user = user("operator");
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        when(productRepository.findWithCategoryAndSupplierById(13L)).thenReturn(Optional.of(product));
        when(appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("operator", "operator"))
                .thenReturn(Optional.of(user));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockMovement result = service.recordStockMovement(request(13L, MovementType.SALE, -2, null));

        assertThat(product.getQuantityOnHand()).isEqualTo(3);
        assertThat(result.getMovementType()).isEqualTo(MovementType.SALE);
        assertThat(result.getQuantityDelta()).isEqualTo(-2);
        assertThat(result.getReason()).isNull();
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void record_withViewerRole_throwsCreateAccessDenied() {
        authenticate("viewer", "ROLE_INVENTORY_VIEWER");
        StockMovementRequest invalidRequest = request(1L, MovementType.PURCHASE, 1, null);

        assertThatThrownBy(() -> service.recordStockMovement(invalidRequest))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("STOCK_MOVEMENT:CREATE permission is required.");

        verifyNoInteractions(productRepository, stockMovementRepository, appUserRepository);
    }

    @Test
    void record_withZeroQuantity_throwsStockMovementException() {
        authenticate("manager", "ROLE_INVENTORY_MANAGER");
        StockMovementRequest invalidRequest = request(1L, MovementType.PURCHASE, 0, null);

        assertThatThrownBy(() -> service.recordStockMovement(invalidRequest))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must not be zero.");

        verifyNoInteractions(productRepository, stockMovementRepository, appUserRepository);
    }

    @Test
    void record_withWrongPositiveNegativeSign_throwsStockMovementException() {
        authenticate("manager", "ROLE_INVENTORY_MANAGER");
        StockMovementRequest invalidPurchase = request(1L, MovementType.PURCHASE, -1, null);
        StockMovementRequest invalidSale = request(1L, MovementType.SALE, 1, null);

        assertThatThrownBy(() -> service.recordStockMovement(invalidPurchase))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must be positive for this movement type.");
        assertThatThrownBy(() -> service.recordStockMovement(invalidSale))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must be negative for this movement type.");

        verifyNoInteractions(productRepository, stockMovementRepository, appUserRepository);
    }

    @Test
    void record_withReasonRequiredAndBlankReason_throwsStockMovementException() {
        authenticate("manager", "ROLE_INVENTORY_MANAGER");
        StockMovementRequest invalidRequest = request(1L, MovementType.DAMAGED, -1, "  ");

        assertThatThrownBy(() -> service.recordStockMovement(invalidRequest))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Reason is required for this movement type.");

        verifyNoInteractions(productRepository, stockMovementRepository, appUserRepository);
    }

    @Test
    void record_withInactiveOrMissingProduct_throwsProductUnavailableException() {
        authenticate("manager", "ROLE_INVENTORY_MANAGER");
        Product inactiveProduct = product(5);
        inactiveProduct.deactivate();
        when(productRepository.findWithCategoryAndSupplierById(21L)).thenReturn(Optional.empty());
        when(productRepository.findWithCategoryAndSupplierById(22L)).thenReturn(Optional.of(inactiveProduct));
        StockMovementRequest missingProductRequest = request(21L, MovementType.PURCHASE, 1, null);
        StockMovementRequest inactiveProductRequest = request(22L, MovementType.PURCHASE, 1, null);

        assertThatThrownBy(() -> service.recordStockMovement(missingProductRequest))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Product is no longer available.");
        assertThatThrownBy(() -> service.recordStockMovement(inactiveProductRequest))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Product is no longer available.");

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(stockMovementRepository, appUserRepository);
    }

    @Test
    void record_whenRepositorySaveFails_wrapsDataAccessException() {
        authenticate("manager", "ROLE_INVENTORY_MANAGER");
        Product product = product(5);
        AppUser user = user("manager");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("bad data");
        when(productRepository.findWithCategoryAndSupplierById(31L)).thenReturn(Optional.of(product));
        when(appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(user));
        when(stockMovementRepository.save(any(StockMovement.class))).thenThrow(failure);
        StockMovementRequest validRequest = request(31L, MovementType.PURCHASE, 2, null);

        assertThatThrownBy(() -> service.recordStockMovement(validRequest))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Failed to save movement. Please try again.")
                .hasCause(failure);

        assertThat(product.getQuantityOnHand()).isEqualTo(7);
        verify(productRepository).save(product);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    private static void authenticate(String username, String... authorities) {
        var grantedAuthorities = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(username, "password", grantedAuthorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static Product product(int quantityOnHand) {
        return new Product(
                "SKU-" + quantityOnHand,
                "Product " + quantityOnHand,
                "Test product",
                BigDecimal.TEN,
                quantityOnHand,
                0,
                new Category("Category", "Test category"),
                new Supplier("Supplier", "Contact", "supplier@example.com", "809-555-0000"),
                true);
    }

    private static AppUser user(String username) {
        return new AppUser(username, username + "@example.com", "{noop}password");
    }

    private static StockMovement movement(
            Product product,
            AppUser user,
            MovementType movementType,
            Integer quantityDelta,
            String reason) {
        return new StockMovement(product, user, movementType, quantityDelta, reason);
    }

    private static StockMovementRequest request(
            Long productId,
            MovementType movementType,
            Integer quantityDelta,
            String reason) {
        StockMovementRequest request = new StockMovementRequest();
        request.setProductId(productId);
        request.setMovementType(movementType);
        request.setQuantityDelta(quantityDelta);
        request.setReason(reason);
        return request;
    }
}
