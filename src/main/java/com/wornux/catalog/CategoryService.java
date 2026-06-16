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
public class CategoryService {

    private static final String VIEWER = "ROLE_INVENTORY_VIEWER";
    private static final String MANAGER = "ROLE_INVENTORY_MANAGER";
    private static final String ADMINISTRATOR = "ROLE_SYSTEM_ADMINISTRATOR";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> search(CategoryFilter filter) {
        requireRead();
        CategoryFilter safeFilter = filter == null ? new CategoryFilter("", null) : filter;
        return categoryRepository.search(normalizeSearch(safeFilter.text()), safeFilter.active());
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        requireRead();
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryException("Category was not found."));
    }

    @Transactional(readOnly = true)
    public long productCount(Long categoryId) {
        requireRead();
        return productRepository.countByCategoryId(categoryId);
    }

    @Transactional(readOnly = true)
    public long activeProductCount(Long categoryId) {
        requireRead();
        return productRepository.countByCategoryIdAndActiveTrue(categoryId);
    }

    @Transactional
    public Category create(@Valid CategoryRequest request) {
        requireManage();
        validateUniqueName(request.getName(), null);
        Category category = new Category(normalizeName(request.getName()), trimToNull(request.getDescription()));
        category.update(category.getName(), category.getDescription(), request.isActive());
        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(Long id, @Valid CategoryRequest request) {
        requireManage();
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryException("Category was not found."));
        if (!Objects.equals(category.getVersion(), request.getVersion())) {
            throw new CategoryException("Category was updated by another user. Refresh the form and try again.");
        }
        validateUniqueName(request.getName(), id);
        category.update(normalizeName(request.getName()), trimToNull(request.getDescription()), request.isActive());
        return categoryRepository.save(category);
    }

    @Transactional
    public void deactivate(Long id) {
        requireManage();
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryException("Category was not found."));
        category.deactivate();
        categoryRepository.save(category);
    }

    public boolean canManageCategories() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return hasAuthority(authentication, MANAGER) || hasAuthority(authentication, ADMINISTRATOR);
    }

    private void requireRead() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!hasAuthority(authentication, VIEWER) && !hasAuthority(authentication, MANAGER)
                && !hasAuthority(authentication, ADMINISTRATOR)) {
            throw new AccessDeniedException("CATEGORY:READ permission is required.");
        }
    }

    private void requireManage() {
        if (!canManageCategories()) {
            throw new AccessDeniedException("CATEGORY:CREATE/UPDATE/DELETE permission is required.");
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> authority.equals(grantedAuthority.getAuthority()));
    }

    private void validateUniqueName(String name, Long id) {
        boolean exists = id == null
                ? categoryRepository.existsByNameIgnoreCase(normalizeName(name))
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizeName(name), id);
        if (exists) {
            throw new CategoryException("Category name already exists. Please choose a different one.");
        }
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
