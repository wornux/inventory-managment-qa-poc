package com.wornux.catalog;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AuthorizationService authorizationService;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            StockMovementRepository stockMovementRepository,
            AuthorizationService authorizationService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(ProductFilter filter, Pageable pageable) {
        requireRead();

        return productRepository.findAll(toSpecification(filter), pageable);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        requireRead();

        return productRepository
                .findWithCategoryAndSupplierById(id)
                .orElseThrow(() -> new ProductException("Product was not found."));
    }

    @Transactional
    public Product create(@Valid ProductRequest request) {
        authorizationService.check(AppPermission.PRODUCT_CREATE);
        validateUniqueSku(request.getSku(), null);
        validateUniqueActiveName(request.getName(), null);
        Category category = requireCategory(request.getCategoryId());
        Supplier supplier = resolveSupplier(request.getSupplierId());
        Product product = new Product(
                normalizeCode(request.getSku()),
                normalizeName(request.getName()),
                trimToNull(request.getDescription()),
                request.getUnitPrice(),
                request.getQuantityOnHand(),
                request.getMinimumStock(),
                category,
                supplier,
                request.isActive());

        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long id, @Valid ProductRequest request) {
        authorizationService.check(AppPermission.PRODUCT_UPDATE);
        Product product = productRepository
                .findWithCategoryAndSupplierById(id)
                .orElseThrow(() -> new ProductException("Product was not found."));

        if (!Objects.equals(product.getVersion(), request.getVersion())) {
            throw new ProductException("Product was updated by another user. Refresh the form and try again.");
        }

        validateUniqueSku(request.getSku(), id);
        validateUniqueActiveName(request.getName(), id);
        product.update(
                normalizeCode(request.getSku()),
                normalizeName(request.getName()),
                trimToNull(request.getDescription()),
                request.getUnitPrice(),
                request.getQuantityOnHand(),
                request.getMinimumStock(),
                requireCategory(request.getCategoryId()),
                resolveSupplier(request.getSupplierId()),
                request.isActive());

        return productRepository.save(product);
    }

    @Transactional
    public void delete(Long id) {
        authorizationService.check(AppPermission.PRODUCT_DELETE);
        Product product = productRepository
                .findWithCategoryAndSupplierById(id)
                .orElseThrow(() -> new ProductException("Product was not found."));

        if (stockMovementRepository.existsByProductId(id)) {
            product.deactivate();
            productRepository.save(product);
            return;
        }

        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<Category> activeCategories() {
        requireRead();

        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Supplier> activeSuppliers() {
        requireRead();

        return supplierRepository.findByActiveTrueOrderByNameAsc();
    }

    public boolean canCreateProducts() {
        return authorizationService.can(AppPermission.PRODUCT_CREATE);
    }

    public boolean canUpdateProducts() {
        return authorizationService.can(AppPermission.PRODUCT_UPDATE);
    }

    public boolean canDeleteProducts() {
        return authorizationService.can(AppPermission.PRODUCT_DELETE);
    }

    private void requireRead() {
        authorizationService.check(AppPermission.PRODUCT_VIEW);
    }

    private void validateUniqueSku(String sku, Long id) {
        boolean exists = id == null
                ? productRepository.existsBySkuIgnoreCase(normalizeCode(sku))
                : productRepository.existsBySkuIgnoreCaseAndIdNot(normalizeCode(sku), id);

        if (exists) {
            throw new ProductException("SKU already exists. Please choose a different one.");
        }
    }

    private void validateUniqueActiveName(String name, Long id) {
        if (productRepository.existsActiveNameExcludingId(normalizeName(name), id)) {
            throw new ProductException("Product name already exists for an active product.");
        }
    }

    private Category requireCategory(Long id) {
        return categoryRepository
                .findById(id)
                .filter(Category::isActive)
                .orElseThrow(() -> new ProductException(
                        "Selected category/supplier is no longer available. Please refresh and try again."));
    }

    private Supplier resolveSupplier(Long id) {
        if (id == null) {
            return null;
        }

        return supplierRepository
                .findById(id)
                .filter(Supplier::isActive)
                .orElseThrow(() -> new ProductException(
                        "Selected category/supplier is no longer available. Please refresh and try again."));
    }

    private Specification<Product> toSpecification(ProductFilter filter) {
        ProductFilter safeFilter = filter == null ? new ProductFilter("", null, null, null, false) : filter;
        String text = normalizeSearch(safeFilter.text());

        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            if (!text.isEmpty()) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), "%" + text + "%"),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + text + "%")));
            }

            if (safeFilter.categoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), safeFilter.categoryId()));
            }

            if (safeFilter.supplierId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("supplier").get("id"), safeFilter.supplierId()));
            }

            if (safeFilter.active() != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), safeFilter.active()));
            }

            if (safeFilter.lowStockOnly()) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("quantityOnHand"), root.get("minimumStock")));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? "" : value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}
