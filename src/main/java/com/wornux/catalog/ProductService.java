package com.wornux.catalog;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ProductService {

    private static final String VIEWER = "ROLE_INVENTORY_VIEWER";
    private static final String MANAGER = "ROLE_INVENTORY_MANAGER";
    private static final String ADMINISTRATOR = "ROLE_SYSTEM_ADMINISTRATOR";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> search(ProductFilter filter) {
        requireRead();
        ProductFilter safeFilter = filter == null ? new ProductFilter("", null, null, null, false) : filter;
        return productRepository.search(
                normalizeSearch(safeFilter.text()),
                safeFilter.categoryId(),
                safeFilter.supplierId(),
                safeFilter.active(),
                safeFilter.lowStockOnly());
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        requireRead();
        return productRepository.findWithCategoryAndSupplierById(id)
                .orElseThrow(() -> new ProductException("Product was not found."));
    }

    @Transactional
    public Product create(@Valid ProductRequest request) {
        requireManage();
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
        requireManage();
        Product product = productRepository.findWithCategoryAndSupplierById(id)
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
        requireManage();
        Product product = productRepository.findWithCategoryAndSupplierById(id)
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

    public boolean canManageProducts() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return hasAuthority(authentication, MANAGER) || hasAuthority(authentication, ADMINISTRATOR);
    }

    private void requireRead() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!hasAuthority(authentication, VIEWER) && !hasAuthority(authentication, MANAGER)
                && !hasAuthority(authentication, ADMINISTRATOR)) {
            throw new AccessDeniedException("PRODUCT:READ permission is required.");
        }
    }

    private void requireManage() {
        if (!canManageProducts()) {
            throw new AccessDeniedException("PRODUCT:CREATE/UPDATE/DELETE permission is required.");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
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
        return categoryRepository.findById(id)
                .filter(Category::isActive)
                .orElseThrow(() -> new ProductException(
                        "Selected category/supplier is no longer available. Please refresh and try again."));
    }

    private Supplier resolveSupplier(Long id) {
        if (id == null) {
            return null;
        }
        return supplierRepository.findById(id)
                .filter(Supplier::isActive)
                .orElseThrow(() -> new ProductException(
                        "Selected category/supplier is no longer available. Please refresh and try again."));
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
