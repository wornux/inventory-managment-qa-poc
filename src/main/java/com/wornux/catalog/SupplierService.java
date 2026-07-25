package com.wornux.catalog;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final AuthorizationService authorizationService;

    public SupplierService(
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            AuthorizationService authorizationService) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.authorizationService = authorizationService;
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
        authorizationService.check(AppPermission.SUPPLIER_CREATE);
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
        authorizationService.check(AppPermission.SUPPLIER_UPDATE);
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
        authorizationService.check(AppPermission.SUPPLIER_DELETE);
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new SupplierException("Supplier was not found."));
        supplier.deactivate();
        supplierRepository.save(supplier);
    }

    public boolean canCreateSuppliers() {
        return authorizationService.can(AppPermission.SUPPLIER_CREATE);
    }

    public boolean canUpdateSuppliers() {
        return authorizationService.can(AppPermission.SUPPLIER_UPDATE);
    }

    public boolean canDeleteSuppliers() {
        return authorizationService.can(AppPermission.SUPPLIER_DELETE);
    }

    private void requireRead() {
        authorizationService.check(AppPermission.SUPPLIER_VIEW);
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
