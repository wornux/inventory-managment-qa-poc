package com.wornux.catalog;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import com.wornux.user.AppUser;
import com.wornux.user.AppUserRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final AppUserRepository appUserRepository;
    private final AuthorizationService authorizationService;

    public StockMovementService(
            StockMovementRepository stockMovementRepository,
            ProductRepository productRepository,
            AppUserRepository appUserRepository,
            AuthorizationService authorizationService) {
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.appUserRepository = appUserRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> search(StockMovementFilter filter, Pageable pageable) {
        requireRead();

        return stockMovementRepository.findAll(toSpecification(filter), pageable);
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

        Product product = productRepository
                .findWithCategoryAndSupplierById(request.getProductId())
                .filter(Product::isActive)
                .orElseThrow(() -> new StockMovementException("Product is no longer available."));

        int resultingStock = product.getQuantityOnHand() + request.getQuantityDelta();

        if (resultingStock < 0) {
            throw new StockMovementException("Insufficient stock. Current stock: " + product.getQuantityOnHand()
                    + ", requested: " + Math.abs(request.getQuantityDelta()) + ".");
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
        return authorizationService.can(AppPermission.STOCK_MOVEMENT_CREATE);
    }

    private void requireRead() {
        authorizationService.check(AppPermission.STOCK_MOVEMENT_VIEW);
    }

    private void requireCreate() {
        authorizationService.check(AppPermission.STOCK_MOVEMENT_CREATE);
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

        return appUserRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(authentication.getName(), authentication.getName())
                .orElse(null);
    }

    private Specification<StockMovement> toSpecification(StockMovementFilter filter) {
        StockMovementFilter safeFilter = filter == null ? new StockMovementFilter(null, null, null, null, "") : filter;

        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            if (safeFilter.createdFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), safeFilter.createdFrom()));
            }

            if (safeFilter.createdTo() != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("createdDate"), safeFilter.createdTo()));
            }

            if (safeFilter.productId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("product").get("id"), safeFilter.productId()));
            }

            if (safeFilter.movementType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("movementType"), safeFilter.movementType()));
            }

            String username = safeFilter.username() == null
                    ? ""
                    : safeFilter.username().trim().toLowerCase(Locale.ROOT);
            if (!username.isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("user").get("username")), username));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeReason(String value) {
        String trimmed = value == null ? "" : value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}
