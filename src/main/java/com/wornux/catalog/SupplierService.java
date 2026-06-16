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
public class SupplierService {

    private static final String VIEWER = "ROLE_INVENTORY_VIEWER";
    private static final String MANAGER = "ROLE_INVENTORY_MANAGER";
    private static final String ADMINISTRATOR = "ROLE_SYSTEM_ADMINISTRATOR";

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public SupplierService(SupplierRepository supplierRepository, ProductRepository productRepository) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Supplier> search(SupplierFilter filter) {
        requireRead();
        SupplierFilter safeFilter = filter == null ? new SupplierFilter("", null) : filter;
        return supplierRepository.search(normalizeSearch(safeFilter.text()), safeFilter.active());
    }

    @Transactional(readOnly = true)
    public Supplier get(Long id) {
        requireRead();
        return supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierException("Supplier was not found."));
    }

    @Transactional(readOnly = true)
    public long productCount(Long supplierId) {
        requireRead();
        return productRepository.countBySupplierId(supplierId);
    }

    @Transactional(readOnly = true)
    public long activeProductCount(Long supplierId) {
        requireRead();
        return productRepository.countBySupplierIdAndActiveTrue(supplierId);
    }

    @Transactional
    public Supplier create(@Valid SupplierRequest request) {
        requireManage();
        Supplier supplier = new Supplier(
                normalizeName(request.getName()),
                trimToNull(request.getContactName()),
                trimToNull(request.getEmail()),
                trimToNull(request.getPhone()));
        supplier.update(
                supplier.getName(),
                supplier.getContactName(),
                supplier.getEmail(),
                supplier.getPhone(),
                request.isActive());
        return supplierRepository.save(supplier);
    }

    @Transactional
    public Supplier update(Long id, @Valid SupplierRequest request) {
        requireManage();
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierException("Supplier was not found."));
        if (!Objects.equals(supplier.getVersion(), request.getVersion())) {
            throw new SupplierException("Supplier was updated by another user. Refresh the form and try again.");
        }
        supplier.update(
                normalizeName(request.getName()),
                trimToNull(request.getContactName()),
                trimToNull(request.getEmail()),
                trimToNull(request.getPhone()),
                request.isActive());
        return supplierRepository.save(supplier);
    }

    @Transactional
    public void deactivate(Long id) {
        requireManage();
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierException("Supplier was not found."));
        supplier.deactivate();
        supplierRepository.save(supplier);
    }

    public boolean canManageSuppliers() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return hasAuthority(authentication, MANAGER) || hasAuthority(authentication, ADMINISTRATOR);
    }

    private void requireRead() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!hasAuthority(authentication, VIEWER) && !hasAuthority(authentication, MANAGER)
                && !hasAuthority(authentication, ADMINISTRATOR)) {
            throw new AccessDeniedException("SUPPLIER:READ permission is required.");
        }
    }

    private void requireManage() {
        if (!canManageSuppliers()) {
            throw new AccessDeniedException("SUPPLIER:CREATE/UPDATE/DELETE permission is required.");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
