package com.wornux.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import com.wornux.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class StockMovementServiceTest {

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AppUserRepository appUserRepository;

    private StockMovementService service;

    @BeforeEach
    void setUp() {
        service = new StockMovementService(
                stockMovementRepository,
                productRepository,
                appUserRepository,
                new AuthorizationService(appUserRepository));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void search_withNullFilter_usesSpecificationAndLedgerOrder() {
        authenticate("viewer", "stock-movement:view");
        List<StockMovement> expected = List.of();
        when(stockMovementRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(expected);

        List<StockMovement> result = service.search(null);

        assertThat(result).isSameAs(expected);
        assertThat(service.search(new StockMovementFilter(null, null, null, null, null)))
                .isSameAs(expected);
        verify(stockMovementRepository, times(2))
                .findAll(
                        any(Specification.class),
                        eq(Sort.by(Sort.Order.desc("createdDate"), Sort.Order.desc("id"))));
    }

    @Test
    void search_withFilter_usesSpecification() {
        authenticate("manager", "stock-movement:create");
        Instant createdFrom = Instant.parse("2026-01-01T00:00:00Z");
        Instant createdTo = Instant.parse("2026-01-31T00:00:00Z");
        StockMovementFilter filter =
                new StockMovementFilter(createdFrom, createdTo, 9L, MovementType.SALE, " manager ");
        List<StockMovement> expected = List.of(movement(product(10), user("manager"), MovementType.SALE, -2, null));
        when(stockMovementRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(expected);

        List<StockMovement> result = service.search(filter);

        assertThat(result).isSameAs(expected);
        verify(stockMovementRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void pagedSearch_usesTheSameFiltersAndPageable() {
        authenticate("manager", "stock-movement:view");
        Instant createdFrom = Instant.parse("2026-01-01T00:00:00Z");
        Instant createdTo = Instant.parse("2026-02-01T00:00:00Z");
        var filter = new StockMovementFilter(createdFrom, createdTo, 9L, MovementType.SALE, " manager ");
        var pageable = PageRequest.of(1, 20);
        var expected = new PageImpl<StockMovement>(List.of(), pageable, 21);
        when(stockMovementRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(expected);

        assertThat(service.search(filter, pageable)).isSameAs(expected);
        verify(stockMovementRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void search_withoutViewPermission_throwsAccessDeniedException() {
        authenticate("outsider", "product:view");

        assertThatThrownBy(() -> service.search(null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Missing permission stock-movement:view");

        verifyNoInteractions(stockMovementRepository);
    }

    @Test
    void activeProducts_withViewPermission_returnsActiveProducts() {
        authenticate("viewer", "stock-movement:view");
        List<Product> expected = List.of(product(4), product(7));
        when(productRepository.findByActiveTrueOrderBySkuAsc()).thenReturn(expected);

        List<Product> result = service.activeProducts();

        assertThat(result).isSameAs(expected);
        verify(productRepository).findByActiveTrueOrderBySkuAsc();
    }

    @Test
    void movementUsernames_withViewPermission_returnsDistinctUsernames() {
        authenticate("operator", "stock-movement:create");
        List<String> expected = List.of("admin", "operator");
        when(stockMovementRepository.findDistinctUsernames()).thenReturn(expected);

        List<String> result = service.movementUsernames();

        assertThat(result).isSameAs(expected);
        verify(stockMovementRepository).findDistinctUsernames();
    }

    @Test
    void canCreateMovements_allowsCreatePermission() {
        authenticate("operator", "stock-movement:create");

        assertThat(service.canCreateMovements()).isTrue();

        authenticate("manager", "stock-movement:create");

        assertThat(service.canCreateMovements()).isTrue();

        authenticate("admin", "stock-movement:create");

        assertThat(service.canCreateMovements()).isTrue();
    }

    @Test
    void canCreateMovements_rejectsViewPermissionAndAnonymous() {
        authenticate("viewer", "stock-movement:view");

        assertThat(service.canCreateMovements()).isFalse();

        SecurityContextHolder.clearContext();

        assertThat(service.canCreateMovements()).isFalse();
    }

    @Test
    void record_withPurchase_updatesProductStockAndSavesMovement() {
        authenticate("manager", "stock-movement:create");
        Product product = product(5);
        AppUser user = user("manager");
        when(productRepository.findWithCategoryAndSupplierById(12L)).thenReturn(Optional.of(product));
        when(appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("manager", "manager"))
                .thenReturn(Optional.of(user));
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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
    void record_reasonRequiredTypeAcceptsMeaningfulReason() {
        authenticate("manager", "stock-movement:create");
        when(productRepository.findWithCategoryAndSupplierById(12L)).thenReturn(Optional.of(product(5)));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.recordStockMovement(request(12L, MovementType.ADJUSTMENT_IN, 1, "count correction"))
                        .getReason())
                .isEqualTo("count correction");
    }

    @Test
    void record_withSale_updatesProductStockAndSavesNegativeMovement() {
        authenticate("operator", "stock-movement:create");
        Product product = product(5);
        AppUser user = user("operator");
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        when(productRepository.findWithCategoryAndSupplierById(13L)).thenReturn(Optional.of(product));
        when(appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("operator", "operator"))
                .thenReturn(Optional.of(user));
        when(stockMovementRepository.save(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockMovement result = service.recordStockMovement(request(13L, MovementType.SALE, -2, null));

        assertThat(product.getQuantityOnHand()).isEqualTo(3);
        assertThat(result.getMovementType()).isEqualTo(MovementType.SALE);
        assertThat(result.getQuantityDelta()).isEqualTo(-2);
        assertThat(result.getReason()).isNull();
        verify(stockMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getUser()).isSameAs(user);
    }

    @Test
    void record_withViewPermission_throwsCreateAccessDenied() {
        authenticate("viewer", "stock-movement:view");
        StockMovementRequest invalidRequest = request(1L, MovementType.PURCHASE, 1, null);

        assertThatThrownBy(() -> service.recordStockMovement(invalidRequest))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Missing permission stock-movement:create");

        verifyNoInteractions(productRepository, stockMovementRepository);
    }

    @Test
    void record_withZeroQuantity_throwsStockMovementException() {
        authenticate("manager", "stock-movement:create");
        StockMovementRequest invalidRequest = request(1L, MovementType.PURCHASE, 0, null);

        assertThatThrownBy(() -> service.recordStockMovement(invalidRequest))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must not be zero.");

        verifyNoInteractions(productRepository, stockMovementRepository);
    }

    @Test
    void record_withNullQuantity_usesSameNonZeroRule() {
        authenticate("manager", "stock-movement:create");

        assertThatThrownBy(() -> service.recordStockMovement(request(1L, MovementType.PURCHASE, null, null)))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must not be zero.");
    }

    @Test
    void record_cannotReduceInventoryBelowZero() {
        authenticate("manager", "stock-movement:create");
        when(productRepository.findWithCategoryAndSupplierById(1L)).thenReturn(Optional.of(product(2)));

        assertThatThrownBy(() -> service.recordStockMovement(request(1L, MovementType.SALE, -3, null)))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Insufficient stock. Current stock: 2, requested: 3.");
        verify(productRepository, never()).save(any());
    }

    @Test
    void record_withoutAuthenticatedOrPersistedUserStillRecordsSystemMovement() {
        authenticate("operator", "stock-movement:create");
        when(productRepository.findWithCategoryAndSupplierById(1L)).thenReturn(Optional.of(product(2)));
        when(stockMovementRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.recordStockMovement(request(1L, MovementType.PURCHASE, 1, null))
                        .getUser())
                .isNull();

        SecurityContextHolder.clearContext();
        AuthorizationService authorization = mock(AuthorizationService.class);
        StockMovementService permissive =
                new StockMovementService(stockMovementRepository, productRepository, appUserRepository, authorization);

        assertThat(permissive
                        .recordStockMovement(request(1L, MovementType.PURCHASE, 1, null))
                        .getUser())
                .isNull();

        var unnamedAuthentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(unnamedAuthentication);

        assertThat(permissive
                        .recordStockMovement(request(1L, MovementType.PURCHASE, 1, null))
                        .getUser())
                .isNull();
    }

    @Test
    void record_withWrongPositiveNegativeSign_throwsStockMovementException() {
        authenticate("manager", "stock-movement:create");
        StockMovementRequest invalidPurchase = request(1L, MovementType.PURCHASE, -1, null);
        StockMovementRequest invalidSale = request(1L, MovementType.SALE, 1, null);

        assertThatThrownBy(() -> service.recordStockMovement(invalidPurchase))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must be positive for this movement type.");
        assertThatThrownBy(() -> service.recordStockMovement(invalidSale))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Quantity delta must be negative for this movement type.");

        verifyNoInteractions(productRepository, stockMovementRepository);
    }

    @Test
    void record_withReasonRequiredAndBlankReason_throwsStockMovementException() {
        authenticate("manager", "stock-movement:create");
        StockMovementRequest invalidRequest = request(1L, MovementType.DAMAGED, -1, "  ");

        assertThatThrownBy(() -> service.recordStockMovement(invalidRequest))
                .isInstanceOf(StockMovementException.class)
                .hasMessage("Reason is required for this movement type.");

        verifyNoInteractions(productRepository, stockMovementRepository);
    }

    @Test
    void record_withInactiveOrMissingProduct_throwsProductUnavailableException() {
        authenticate("manager", "stock-movement:create");
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
        verifyNoInteractions(stockMovementRepository);
    }

    @Test
    void record_whenRepositorySaveFails_wrapsDataAccessException() {
        authenticate("manager", "stock-movement:create");
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

    private void authenticate(String username, String... authorities) {
        var permissions = Arrays.stream(authorities)
                .map(AppPermission::fromCode)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Role role = new Role("TEST", "Test", null, false);
        role.update(role.getName(), role.getDescription(), true, permissions);
        AppUser user = user(username);
        user.addRole(role);
        when(appUserRepository.findForAuthorization(username)).thenReturn(Optional.of(user));

        var grantedAuthorities =
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(username, "password", grantedAuthorities));
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
        return new AppUser(username, username + "@example.com", "https://issuer.example.test", username + "-subject");
    }

    private static StockMovement movement(
            Product product, AppUser user, MovementType movementType, Integer quantityDelta, String reason) {
        return new StockMovement(product, user, movementType, quantityDelta, reason);
    }

    private static StockMovementRequest request(
            Long productId, MovementType movementType, Integer quantityDelta, String reason) {
        StockMovementRequest request = new StockMovementRequest();
        request.setProductId(productId);
        request.setMovementType(movementType);
        request.setQuantityDelta(quantityDelta);
        request.setReason(reason);

        return request;
    }
}
