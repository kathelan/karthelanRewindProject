package pl.kathelan.mapstruct.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import pl.kathelan.mapstruct.dto.OrderResponseDTO;
import pl.kathelan.mapstruct.dto.OrderSummaryDTO;
import pl.kathelan.mapstruct.model.Order;

/**
 * FEATURE 33: Decorator pattern — przechwytuje wygenerowane mapowanie
 * i wzbogaca wynik o dodatkową logikę (np. formatowanie, audit).
 *
 * W Spring: @DecoratedWith generuje dwa beany:
 *   - wygenerowany impl (kwalifikowany @Delegate)
 *   - ten dekorator jako główny bean @Primary
 */
public abstract class OrderMapperDecorator implements OrderMapper {

    @Autowired
    @Qualifier("delegate")
    private OrderMapper delegate;

    @Override
    public OrderResponseDTO toResponseDto(Order order) {
        // Wywołaj wygenerowany mapper
        OrderResponseDTO dto = delegate.toResponseDto(order);

        // Wzbogać o status w języku polskim (używa @Named "statusLabel" pośrednio)
        if (dto != null && order.getStatus() != null) {
            dto.setStatus(delegate.statusToLabel(order.getStatus()));
        }

        return dto;
    }

    @Override
    public OrderSummaryDTO toSummaryDto(Order order) {
        OrderSummaryDTO dto = delegate.toSummaryDto(order);

        // Dekorator wzbogaca status mobilnego widoku tak samo
        if (dto != null && order.getStatus() != null) {
            dto.setStatus(delegate.statusToLabel(order.getStatus()));
        }

        return dto;
    }
}
