package com.wornux.api.product;

import com.wornux.api.AbstractRestController;
import com.wornux.api.ApiResponse;
import com.wornux.api.OpenApiConfig;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductFilter;
import com.wornux.catalog.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Product inventory CRUD")
@SecurityRequirement(name = OpenApiConfig.OAUTH2_SCHEME)
public class ProductController extends AbstractRestController {

    private final ProductService productService;
    private final ProductApiMapper productApiMapper;

    public ProductController(ProductService productService, ProductApiMapper productApiMapper) {
        this.productService = productService;
        this.productApiMapper = productApiMapper;
    }

    @GetMapping
    @Operation(summary = "List products", description = "Returns a pageable product list with optional filters.")
    ResponseEntity<ApiResponse<List<ProductResponseDto>>> list(
            @RequestParam(defaultValue = "") String text,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            Pageable pageable) {
        Page<ProductResponseDto> products = productService
                .search(new ProductFilter(text, categoryId, supplierId, active, lowStockOnly), pageable)
                .map(productApiMapper::toResponse);

        return ok("Products retrieved.", products);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product", description = "Returns one product by identifier.")
    ResponseEntity<ApiResponse<ProductResponseDto>> get(@PathVariable Long id) {
        return ok("Product retrieved.", productApiMapper.toResponse(productService.get(id)));
    }

    @PostMapping
    @Operation(summary = "Create product", description = "Creates a product and returns its API representation.")
    ResponseEntity<ApiResponse<ProductResponseDto>> create(@Valid @RequestBody ProductRequestDto request) {
        Product product = productService.create(productApiMapper.toDomainRequest(request));
        ProductResponseDto response = productApiMapper.toResponse(product);

        return created("Product created.", URI.create("/api/products/" + product.getId()), response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product", description = "Updates an existing product using optimistic locking.")
    ResponseEntity<ApiResponse<ProductResponseDto>> update(
            @PathVariable Long id, @Valid @RequestBody ProductRequestDto request) {
        Product product = productService.update(id, productApiMapper.toDomainRequest(request));

        return ok("Product updated.", productApiMapper.toResponse(product));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", description = "Deletes or deactivates a product according to domain rules.")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productService.delete(id);

        return noContentMessage("Product deleted or deactivated.");
    }
}
