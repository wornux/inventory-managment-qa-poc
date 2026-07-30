package com.wornux.api.catalog;

import com.wornux.api.AbstractRestController;
import com.wornux.api.ApiResponse;
import com.wornux.api.OpenApiConfig;
import com.wornux.api.product.CatalogReferenceResponseDto;
import com.wornux.catalog.CategoryFilter;
import com.wornux.catalog.CategoryService;
import com.wornux.catalog.SupplierFilter;
import com.wornux.catalog.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Catalog lookups", description = "Category and supplier options for inventory clients")
@SecurityRequirement(name = OpenApiConfig.OAUTH2_SCHEME)
public class CatalogLookupController extends AbstractRestController {

    private final CategoryService categoryService;
    private final SupplierService supplierService;

    public CatalogLookupController(CategoryService categoryService, SupplierService supplierService) {
        this.categoryService = categoryService;
        this.supplierService = supplierService;
    }

    @GetMapping("/api/categories")
    @Operation(summary = "List category options")
    ResponseEntity<ApiResponse<List<CatalogReferenceResponseDto>>> categories(
            @RequestParam(defaultValue = "") String text, @RequestParam(defaultValue = "true") Boolean active) {
        List<CatalogReferenceResponseDto> categories = categoryService.search(new CategoryFilter(text, active)).stream()
                .map(category -> new CatalogReferenceResponseDto(category.getId(), category.getName()))
                .toList();

        return ok("Categories retrieved.", categories);
    }

    @GetMapping("/api/suppliers")
    @Operation(summary = "List supplier options")
    ResponseEntity<ApiResponse<List<CatalogReferenceResponseDto>>> suppliers(
            @RequestParam(defaultValue = "") String text, @RequestParam(defaultValue = "true") Boolean active) {
        List<CatalogReferenceResponseDto> suppliers = supplierService.search(new SupplierFilter(text, active)).stream()
                .map(supplier -> new CatalogReferenceResponseDto(supplier.getId(), supplier.getName()))
                .toList();

        return ok("Suppliers retrieved.", suppliers);
    }
}
