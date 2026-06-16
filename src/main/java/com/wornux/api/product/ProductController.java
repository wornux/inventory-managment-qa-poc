package com.wornux.api.product;

import com.wornux.api.AbstractRestController;
import com.wornux.api.ApiResponse;
import com.wornux.catalog.Product;
import com.wornux.catalog.ProductFilter;
import com.wornux.catalog.ProductService;
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
public class ProductController extends AbstractRestController {

    private final ProductService productService;
    private final ProductApiMapper productApiMapper;

    public ProductController(ProductService productService, ProductApiMapper productApiMapper) {
        this.productService = productService;
        this.productApiMapper = productApiMapper;
    }

    @GetMapping
    ResponseEntity<ApiResponse<List<ProductResponse>>> list(
            @RequestParam(defaultValue = "") String text,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "false") boolean lowStockOnly,
            Pageable pageable) {
        Page<ProductResponse> products = productService.search(
                        new ProductFilter(text, categoryId, supplierId, active, lowStockOnly),
                        pageable)
                .map(productApiMapper::toResponse);
        return ok("Products retrieved.", products);
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<ProductResponse>> get(@PathVariable Long id) {
        return ok("Product retrieved.", productApiMapper.toResponse(productService.get(id)));
    }

    @PostMapping
    ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        Product product = productService.create(productApiMapper.toDomainRequest(request));
        ProductResponse response = productApiMapper.toResponse(product);
        return created("Product created.", URI.create("/api/products/" + product.getId()), response);
    }

    @PutMapping("/{id}")
    ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request) {
        Product product = productService.update(id, productApiMapper.toDomainRequest(request));
        return ok("Product updated.", productApiMapper.toResponse(product));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productService.delete(id);
        return noContentMessage("Product deleted or deactivated.");
    }
}
