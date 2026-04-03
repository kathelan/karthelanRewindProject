package pl.kathelan.mapstruct.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.kathelan.mapstruct.dto.OrderResponseDTO;
import pl.kathelan.mapstruct.dto.OrderSummaryDTO;
import pl.kathelan.mapstruct.dto.OrderUpdateRequest;
import pl.kathelan.mapstruct.external.ExternalOrderPayload;
import pl.kathelan.mapstruct.mapper.ExternalOrderMapper;
import pl.kathelan.mapstruct.mapper.OrderMapper;
import pl.kathelan.mapstruct.model.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class OrderMappingService {

    private final OrderMapper orderMapper;
    private final ExternalOrderMapper externalOrderMapper;

    private final Map<Long, Order> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    /** Importuje zamówienie z zewnętrznego systemu i zwraca pełny DTO. */
    public OrderResponseDTO importFromExternal(ExternalOrderPayload payload) {
        Order order = externalOrderMapper.toOrder(payload);
        order.setId(idSequence.getAndIncrement());
        order.setTotalAmount(calculateTotal(order));
        store.put(order.getId(), order);
        return orderMapper.toResponseDto(order);
    }

    /** Zwraca pełny widok zamówienia (web). */
    public OrderResponseDTO getOrderResponse(Long id) {
        Order order = getOrThrow(id);
        return orderMapper.toResponseDto(order);
    }

    /** Zwraca uproszczony widok (mobile). */
    public OrderSummaryDTO getOrderSummary(Long id) {
        Order order = getOrThrow(id);
        return orderMapper.toSummaryDto(order);
    }

    /** Aktualizuje zamówienie (PATCH) — demonstruje @MappingTarget. */
    public OrderResponseDTO updateOrder(Long id, OrderUpdateRequest request) {
        Order order = getOrThrow(id);
        orderMapper.updateOrder(request, order);   // @MappingTarget — mutuje istniejący obiekt
        return orderMapper.toResponseDto(order);
    }

    /** Zwraca uproszczone widoki wszystkich zamówień. */
    public List<OrderSummaryDTO> listSummaries() {
        return store.values().stream()
                .map(orderMapper::toSummaryDto)
                .toList();
    }

    // Pomocnicze

    private Order getOrThrow(Long id) {
        Order order = store.get(id);
        if (order == null) throw new IllegalArgumentException("Order not found: " + id);
        return order;
    }

    private BigDecimal calculateTotal(Order order) {
        if (order.getLines() == null) return BigDecimal.ZERO;
        return order.getLines().stream()
                .map(line -> line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
