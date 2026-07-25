package com.wornux.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.wornux.security.authorization.AuthorizationService;
import com.wornux.security.permission.AppPermission;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock CategoryRepository categories;
    @Mock ProductRepository products;
    @Mock AuthorizationService authorization;
    CategoryService service;

    @BeforeEach void setUp() { service = new CategoryService(categories, products, authorization); }

    @Test void readsNormalizeFiltersAndDelegateCounts() {
        Category inactive = new Category("Archived", null);
        when(categories.search("", null)).thenReturn(List.of());
        when(categories.search("tools", true)).thenReturn(List.of(new Category("Tools", null)));
        when(categories.search("", false)).thenReturn(List.of(inactive));
        assertThat(service.search(null)).isEmpty();
        assertThat(service.search(new CategoryFilter("  TOOLS ", true))).hasSize(1);
        assertThat(service.search(new CategoryFilter(null, false))).containsExactly(inactive);
        when(products.countByCategoryId(2L)).thenReturn(4L);
        when(products.countByCategoryIdAndActiveTrue(2L)).thenReturn(3L);
        assertThat(service.productCount(2L)).isEqualTo(4);
        assertThat(service.activeProductCount(2L)).isEqualTo(3);
        verify(authorization, times(5)).check(AppPermission.CATEGORY_VIEW);
    }

    @Test void getReportsMissingCategory() {
        Category category = new Category("Tools", null);
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        assertThat(service.get(1L)).isSameAs(category);
        assertThatThrownBy(() -> service.get(2L)).isInstanceOf(CategoryException.class).hasMessage("Category was not found.");
    }

    @Test void createNormalizesAndRejectsDuplicateNames() {
        CategoryRequest request = request("  Tools  ", "  useful  ", false, null);
        when(categories.save(any())).thenAnswer(i -> i.getArgument(0));
        Category saved = service.create(request);
        assertThat(saved.getName()).isEqualTo("Tools");
        assertThat(saved.getDescription()).isEqualTo("useful");
        assertThat(saved.isActive()).isFalse();
        when(categories.existsByNameIgnoreCase("")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request(null, " ", true, null)))
                .isInstanceOf(CategoryException.class).hasMessageContaining("already exists");
    }

    @Test void updateEnforcesExistenceVersionAndUniqueName() {
        Category category = new Category("Old", null);
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        assertThatThrownBy(() -> service.update(1L, request("New", null, true, 2L))).hasMessageContaining("another user");
        assertThatThrownBy(() -> service.update(9L, request("New", null, true, null))).hasMessage("Category was not found.");
        when(categories.existsByNameIgnoreCaseAndIdNot("New", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(1L, request(" New ", null, true, null))).hasMessageContaining("already exists");
        when(categories.existsByNameIgnoreCaseAndIdNot("New", 1L)).thenReturn(false);
        when(categories.save(category)).thenReturn(category);
        assertThat(service.update(1L, request(" New ", " ", false, null))).isSameAs(category);
        assertThat(category.getDescription()).isNull();
        assertThat(category.isActive()).isFalse();
        assertThat(service.update(1L, request("New", null, true, null)).getDescription()).isNull();
    }

    @Test void deactivateAndCapabilitiesReflectAuthorization() {
        Category category = new Category("Tools", null);
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        service.deactivate(1L);
        assertThat(category.isActive()).isFalse();
        assertThatThrownBy(() -> service.deactivate(2L)).hasMessage("Category was not found.");
        when(authorization.can(AppPermission.CATEGORY_CREATE)).thenReturn(true);
        when(authorization.can(AppPermission.CATEGORY_UPDATE)).thenReturn(false);
        when(authorization.can(AppPermission.CATEGORY_DELETE)).thenReturn(true);
        assertThat(service.canCreateCategories()).isTrue();
        assertThat(service.canUpdateCategories()).isFalse();
        assertThat(service.canDeleteCategories()).isTrue();
    }

    private static CategoryRequest request(String name, String description, boolean active, Long version) {
        CategoryRequest r = new CategoryRequest(); r.setName(name); r.setDescription(description); r.setActive(active); r.setVersion(version); return r;
    }
}
