package com.wornux.api.stockmovement;

import com.wornux.api.AbstractRestController;
import com.wornux.api.ApiResponse;
import com.wornux.api.OpenApiConfig;
import com.wornux.catalog.MovementType;
import com.wornux.catalog.StockMovement;
import com.wornux.catalog.StockMovementFilter;
import com.wornux.catalog.StockMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock-movements")
@Tag(name = "Stock movements", description = "Inventory stock ledger")
@SecurityRequirement(name = OpenApiConfig.OAUTH2_SCHEME)
public class StockMovementController extends AbstractRestController {

    private final StockMovementService stockMovementService;
    private final StockMovementApiMapper stockMovementApiMapper;

    public StockMovementController(
            StockMovementService stockMovementService, StockMovementApiMapper stockMovementApiMapper) {
        this.stockMovementService = stockMovementService;
        this.stockMovementApiMapper = stockMovementApiMapper;
    }

    @GetMapping
    @Operation(summary = "Search stock movements", description = "Dates use an inclusive start and exclusive end.")
    ResponseEntity<ApiResponse<List<StockMovementResponseDto>>> search(
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(defaultValue = "") String username,
            @PageableDefault(
                            sort = {"createdDate", "id"},
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        Page<StockMovementResponseDto> movements = stockMovementService
                .search(new StockMovementFilter(createdFrom, createdTo, productId, movementType, username), pageable)
                .map(stockMovementApiMapper::toResponse);

        return ok("Stock movements retrieved.", movements);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record stock movement")
    ResponseEntity<ApiResponse<StockMovementResponseDto>> record(@Valid @RequestBody StockMovementRequestDto request) {
        StockMovement movement =
                stockMovementService.recordStockMovement(stockMovementApiMapper.toDomainRequest(request));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stock movement recorded.", stockMovementApiMapper.toResponse(movement)));
    }
}
