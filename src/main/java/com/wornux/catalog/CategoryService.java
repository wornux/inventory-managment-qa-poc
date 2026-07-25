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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class CategoryService {

    private static final Sort CATEGORY_ORDER = Sort.by(Sort.Order.asc("name").ignoreCase());
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AuthorizationService authorizationService;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            AuthorizationService authorizationService) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<Category> search(CategoryFilter filter) {
        requireRead();

        return categoryRepository.findAll(toSpecification(filter), CATEGORY_ORDER);
    }

    @Transactional(readOnly = true)
    public Page<Category> search(CategoryFilter filter, Pageable pageable) {
        requireRead();

        return categoryRepository.findAll(toSpecification(filter), pageable);
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        requireRead();

        return categoryRepository.findById(id).orElseThrow(() -> new CategoryException("Category was not found."));
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
        authorizationService.check(AppPermission.CATEGORY_CREATE);
        validateUniqueName(request.getName(), null);
        Category category = new Category(normalizeName(request.getName()), trimToNull(request.getDescription()));
        category.update(category.getName(), category.getDescription(), request.isActive());

        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(Long id, @Valid CategoryRequest request) {
        authorizationService.check(AppPermission.CATEGORY_UPDATE);
        Category category =
                categoryRepository.findById(id).orElseThrow(() -> new CategoryException("Category was not found."));

        if (!Objects.equals(category.getVersion(), request.getVersion())) {
            throw new CategoryException("Category was updated by another user. Refresh the form and try again.");
        }

        validateUniqueName(request.getName(), id);
        category.update(normalizeName(request.getName()), trimToNull(request.getDescription()), request.isActive());

        return categoryRepository.save(category);
    }

    @Transactional
    public void deactivate(Long id) {
        authorizationService.check(AppPermission.CATEGORY_DELETE);
        Category category =
                categoryRepository.findById(id).orElseThrow(() -> new CategoryException("Category was not found."));
        category.deactivate();
        categoryRepository.save(category);
    }

    public boolean canCreateCategories() {
        return authorizationService.can(AppPermission.CATEGORY_CREATE);
    }

    public boolean canUpdateCategories() {
        return authorizationService.can(AppPermission.CATEGORY_UPDATE);
    }

    public boolean canDeleteCategories() {
        return authorizationService.can(AppPermission.CATEGORY_DELETE);
    }

    private void requireRead() {
        authorizationService.check(AppPermission.CATEGORY_VIEW);
    }

    private void validateUniqueName(String name, Long id) {
        boolean exists = id == null
                ? categoryRepository.existsByNameIgnoreCase(normalizeName(name))
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizeName(name), id);

        if (exists) {
            throw new CategoryException("Category name already exists. Please choose a different one.");
        }
    }

    private Specification<Category> toSpecification(CategoryFilter filter) {
        CategoryFilter safeFilter = filter == null ? new CategoryFilter("", null) : filter;
        String text = normalizeSearch(safeFilter.text());

        return (root, query, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();

            if (!text.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + text + "%"));
            }

            if (safeFilter.active() != null) {
                predicates.add(criteriaBuilder.equal(root.get("active"), safeFilter.active()));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
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
