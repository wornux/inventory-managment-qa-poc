package com.wornux.api.stockmovement;

import com.wornux.catalog.StockMovement;
import com.wornux.catalog.StockMovementRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StockMovementApiMapper {

    StockMovementRequest toDomainRequest(StockMovementRequestDto request);

    @Mapping(target = "username", source = "user.username")
    StockMovementResponseDto toResponse(StockMovement movement);
}
