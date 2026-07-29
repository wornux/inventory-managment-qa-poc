package com.wornux.api.stockmovement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wornux.catalog.MovementType;
import com.wornux.catalog.Product;
import com.wornux.catalog.StockMovement;
import com.wornux.catalog.StockMovementFilter;
import com.wornux.catalog.StockMovementService;
import com.wornux.user.AppUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StockMovementApiTest {

    @Mock
    private StockMovementService service;

    private final StockMovementApiMapper mapper = Mappers.getMapper(StockMovementApiMapper.class);

    @Test
    void mapperPreservesRequestAndLedgerResponseFields() {
        assertThat(mapper.toDomainRequest(null)).isNull();
        assertThat(mapper.toResponse(null)).isNull();

        Product product = mock(Product.class);
        AppUser user = mock(AppUser.class);
        StockMovement movement = mock(StockMovement.class);
        Instant createdAt = Instant.parse("2026-07-25T10:00:00Z");
        when(product.getId()).thenReturn(4L);
        when(product.getSku()).thenReturn("SKU-4");
        when(product.getName()).thenReturn("Hammer");
        when(user.getUsername()).thenReturn("operator");
        when(movement.getId()).thenReturn(7L);
        when(movement.getCreatedAt()).thenReturn(createdAt);
        when(movement.getProduct()).thenReturn(product);
        when(movement.getUser()).thenReturn(user);
        when(movement.getMovementType()).thenReturn(MovementType.SALE);
        when(movement.getQuantityDelta()).thenReturn(-2);
        when(movement.getReason()).thenReturn("sold");
        var request = new StockMovementRequestDto(4L, MovementType.SALE, -2, "sold");

        assertThat(mapper.toDomainRequest(request))
                .extracting("productId", "movementType", "quantityDelta", "reason")
                .containsExactly(4L, MovementType.SALE, -2, "sold");
        assertThat(mapper.toResponse(movement))
                .isEqualTo(new StockMovementResponseDto(
                        7L,
                        createdAt,
                        new StockMovementProductResponseDto(4L, "SKU-4", "Hammer"),
                        MovementType.SALE,
                        -2,
                        "operator",
                        "sold"));
    }

    @Test
    void mapperPreservesMissingOptionalRelationships() {
        StockMovement movement = mock(StockMovement.class);

        assertThat(mapper.toResponse(movement))
                .extracting(StockMovementResponseDto::product, StockMovementResponseDto::username)
                .containsExactly(null, null);
    }

    @Test
    void controllerSearchCombinesFiltersAndPagination() {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-08-01T00:00:00Z");
        var pageable = PageRequest.of(1, 20);
        StockMovement movement = mock(StockMovement.class);
        Product product = mock(Product.class);
        when(movement.getProduct()).thenReturn(product);
        when(service.search(any(), any())).thenReturn(new PageImpl<>(List.of(movement), pageable, 21));
        var controller = new StockMovementController(service, mapper);

        var response = controller
                .search(from, to, 4L, MovementType.SALE, "operator", pageable)
                .getBody();

        assertThat(response.page().number()).isEqualTo(1);
        ArgumentCaptor<StockMovementFilter> filter = ArgumentCaptor.forClass(StockMovementFilter.class);
        verify(service).search(filter.capture(), eq(pageable));
        assertThat(filter.getValue()).isEqualTo(new StockMovementFilter(from, to, 4L, MovementType.SALE, "operator"));
    }

    @Test
    void controllerRecordsMovementAndReturnsCreated() {
        StockMovement movement = mock(StockMovement.class);
        Product product = mock(Product.class);
        when(movement.getProduct()).thenReturn(product);
        when(service.recordStockMovement(any())).thenReturn(movement);
        var controller = new StockMovementController(service, mapper);

        var result = controller.record(new StockMovementRequestDto(4L, MovementType.PURCHASE, 3, null));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody().message()).isEqualTo("Stock movement recorded.");
        verify(service).recordStockMovement(any());
    }
}
