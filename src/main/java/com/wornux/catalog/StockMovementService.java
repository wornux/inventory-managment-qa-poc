package com.wornux.catalog;

import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class StockMovementService {

    private static final Instant LEDGER_START = java.time.Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant LEDGER_END = java.time.Instant.parse("9999-12-31T00:00:00Z");
    private static final String VIEWER = "ROLE_INVENTORY_VIEWER";
    private static final String OPERATOR = "ROLE_WAREHOUSE_OPERATOR";
    private static final String MANAGER = "ROLE_INVENTORY_MANAGER";
    private static final String ADMINISTRATOR = "ROLE_SYSTEM_ADMINISTRATOR";

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository appUserRepository;

    public StockMovementService(
            StockMovementRepository stockMovementRepository,
            ProductRepository productRepository,
            AppUserRepository appUserRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<StockMovement> search(StockMovementFilter filter) {
        requireRead();
        StockMovementFilter safeFilter = filter == null
                ? new StockMovementFilter(null, null, null, null, "")
                : filter;
        return stockMovementRepository.search(
                safeFilter.createdFrom() == null ? LEDGER_START : safeFilter.createdFrom(),
                safeFilter.createdTo() == null ? LEDGER_END : safeFilter.createdTo(),
                safeFilter.productId(),
                safeFilter.movementType(),
                normalizeUsername(safeFilter.username()));
    }

    @Transactional(readOnly = true)
    public List<Product> activeProducts() {
        requireRead();
        return productRepository.findByActiveTrueOrderBySkuAsc();
    }

    @Transactional(readOnly = true)
    public List<String> movementUsernames() {
        requireRead();
        return stockMovementRepository.findDistinctUsernames();
    }

    @Transactional
    public StockMovement recordStockMovement(@Valid StockMovementRequest request) {
        requireCreate();
        validateQuantityDelta(request.getMovementType(), request.getQuantityDelta());
        String reason = normalizeReason(request.getReason());
        validateReason(request.getMovementType(), reason);

        Product product = productRepository.findWithCategoryAndSupplierById(request.getProductId())
                .filter(Product::isActive)
                .orElseThrow(() -> new StockMovementException("Product is no longer available."));

        int resultingStock = product.getQuantityOnHand() + request.getQuantityDelta();
        if (resultingStock < 0) {
            throw new StockMovementException("Insufficient stock. Current stock: "
                    + product.getQuantityOnHand() + ", requested: " + Math.abs(request.getQuantityDelta()) + ".");
        }

        AppUser user = currentUser();
        try {
            product.applyQuantityDelta(request.getQuantityDelta());
            productRepository.save(product);
            return stockMovementRepository.save(
                    new StockMovement(product, user, request.getMovementType(), request.getQuantityDelta(), reason));
        } catch (DataAccessException exception) {
            throw new StockMovementException("Failed to save movement. Please try again.", exception);
        }
    }

    public boolean canCreateMovements() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return hasAuthority(authentication, OPERATOR)
                || hasAuthority(authentication, MANAGER)
                || hasAuthority(authentication, ADMINISTRATOR);
    }

    private void requireRead() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!hasAuthority(authentication, VIEWER)
                && !hasAuthority(authentication, OPERATOR)
                && !hasAuthority(authentication, MANAGER)
                && !hasAuthority(authentication, ADMINISTRATOR)) {
            throw new AccessDeniedException("STOCK_MOVEMENT:READ permission is required.");
        }
    }

    private void requireCreate() {
        if (!canCreateMovements()) {
            throw new AccessDeniedException("STOCK_MOVEMENT:CREATE permission is required.");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private void validateQuantityDelta(MovementType movementType, Integer quantityDelta) {
        if (quantityDelta == null || quantityDelta == 0) {
            throw new StockMovementException("Quantity delta must not be zero.");
        }
        if (movementType.isPositive() && quantityDelta < 0) {
            throw new StockMovementException("Quantity delta must be positive for this movement type.");
        }
        if (movementType.isNegative() && quantityDelta > 0) {
            throw new StockMovementException("Quantity delta must be negative for this movement type.");
        }
    }

    private void validateReason(MovementType movementType, String reason) {
        if (movementType.isReasonRequired() && reason == null) {
            throw new StockMovementException("Reason is required for this movement type.");
        }
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return appUserRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), authentication.getName())
                .orElse(null);
    }

    private String normalizeReason(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
    }
}
