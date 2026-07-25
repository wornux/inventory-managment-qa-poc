package com.wornux.api.stockmovement;

import com.wornux.catalog.StockMovement;
import org.springframework.stereotype.Component;

@Component
public class StockMovementApiMapper {

    public com.wornux.catalog.StockMovementRequest toDomainRequest(StockMovementRequestDto request) {
        var domain = new com.wornux.catalog.StockMovementRequest();
        domain.setProductId(request.productId());
        domain.setMovementType(request.movementType());
        domain.setQuantityDelta(request.quantityDelta());
        domain.setReason(request.reason());

        return domain;
    }

    public StockMovementResponseDto toResponse(StockMovement movement) {
        var product = movement.getProduct();

        return new StockMovementResponseDto(
                movement.getId(),
                movement.getCreatedAt(),
                new StockMovementProductResponseDto(product.getId(), product.getSku(), product.getName()),
                movement.getMovementType(),
                movement.getQuantityDelta(),
                movement.getUser() == null ? null : movement.getUser().getUsername(),
                movement.getReason());
    }
}
