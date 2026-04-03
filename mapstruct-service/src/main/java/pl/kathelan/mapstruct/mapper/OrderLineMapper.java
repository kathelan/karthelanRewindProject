package pl.kathelan.mapstruct.mapper;

import org.mapstruct.*;
import pl.kathelan.mapstruct.dto.OrderLineDTO;
import pl.kathelan.mapstruct.model.OrderLine;

import java.math.BigDecimal;
import java.util.List;

@Mapper(config = MapStructConfig.class, uses = {ProductMapper.class})
public interface OrderLineMapper {

    // FEATURE 11: Nested property mapping — produkt.sku → productSku, produkt.name → productName
    // FEATURE 12: expression dla formatowania BigDecimal → String
    @Mapping(target = "productSku",          source = "product.sku")
    @Mapping(target = "productName",         source = "product.name")
    @Mapping(target = "unitPriceFormatted",  expression = "java(formatAmount(orderLine.getUnitPrice()))")
    @Mapping(target = "lineTotalFormatted",  expression = "java(formatAmount(orderLine.getLineTotal()))")
    @Mapping(target = "discountPercent",     ignore = true)   // uzupełniany przez @AfterMapping
    OrderLineDTO toDto(OrderLine orderLine);

    // FEATURE 13: @IterableMapping — lista linii (MapStruct wykrywa toDto automatycznie)
    List<OrderLineDTO> toDtoList(List<OrderLine> lines);

    // FEATURE 14: @AfterMapping — logika po mapowaniu; ustawiamy discountPercent tylko gdy > 0
    // Demonstruje też @MappingTarget w hookach
    @AfterMapping
    default void setDiscountIfPresent(OrderLine source, @MappingTarget OrderLineDTO target) {
        BigDecimal discount = source.getDiscountPercent();
        if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
            target.setDiscountPercent(discount.toPlainString() + "%");
        }
        // gdy null lub 0 — discountPercent pozostaje null (zachowanie domyślne NullValuePropertyMappingStrategy.IGNORE)
    }

    // FEATURE 15: @Condition — warunkowe mapowanie pola; jeśli false → pole jest pomijane
    // Tutaj używamy jako alternatywny mechanizm do @AfterMapping (pokazujemy OBA podejścia)
    @Condition
    default boolean isPositiveDiscount(BigDecimal discount) {
        return discount != null && discount.compareTo(BigDecimal.ZERO) > 0;
    }

    // Metoda pomocnicza — formatowanie kwoty
    default String formatAmount(BigDecimal amount) {
        if (amount == null) return "0,00 PLN";
        return String.format("%,.2f PLN", amount);
    }
}
