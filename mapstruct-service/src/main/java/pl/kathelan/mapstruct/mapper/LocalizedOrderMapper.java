package pl.kathelan.mapstruct.mapper;

import org.mapstruct.*;
import pl.kathelan.mapstruct.config.CurrencyContext;
import pl.kathelan.mapstruct.dto.*;
import pl.kathelan.mapstruct.external.ExternalProductType;
import pl.kathelan.mapstruct.mapper.qualifier.MobileFormat;
import pl.kathelan.mapstruct.mapper.qualifier.WebFormat;
import pl.kathelan.mapstruct.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;

/**
 * Zaawansowany mapper demonstrujący funkcjonalności MapStruct których nie ma w OrderMapper:
 * @Context, custom @Qualifier, multiple source params, defaultValue, defaultExpression,
 * @SubclassMapping, Stream mapping, @EnumMapping, @ObjectFactory (via OrderFactory).
 */
@Mapper(
    config = MapStructConfig.class,
    uses = {CustomerMapper.class, AddressMapper.class, OrderLineMapper.class, OrderFactory.class},
    imports = {LocalDateTime.class}
)
public interface LocalizedOrderMapper {

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 34: @Context — przekazywanie stanu zewnętrznego do metod mappera
    // CurrencyContext jest niewidoczny dla samego mapowania (nie jest źródłem),
    // ale dostępny we wszystkich default metodach i sub-mapperach które go przyjmują.
    // ─────────────────────────────────────────────────────────────────────────────

    @Mapping(target = "status",      source = "status")
    @Mapping(target = "totalAmount", expression = "java(context.formatAmount(order.getTotalAmount()))")
    @Mapping(target = "createdAt",   source = "createdAt", dateFormat = "dd.MM.yyyy HH:mm")
    @Mapping(target = "lineCount",   expression = "java(order.getLines().size())")
    OrderResponseDTO toLocalizedDto(Order order, @Context CurrencyContext context);

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 35: Custom @Qualifier annotation — typ-bezpieczna selekcja metody
    // Zamiast @Named("webFormat") używamy @WebFormat — kompilator sprawdza poprawność.
    // ─────────────────────────────────────────────────────────────────────────────

    @Mapping(target = "status",      source = "status")
    @Named("toWebDto")
    @Mapping(target = "totalAmount", qualifiedBy = WebFormat.class, source = "totalAmount")
    @Mapping(target = "createdAt",   source = "createdAt", dateFormat = "dd.MM.yyyy HH:mm")
    @Mapping(target = "lineCount",   expression = "java(order.getLines().size())")
    OrderResponseDTO toWebDto(Order order);

    @Mapping(target = "status",      source = "status")
    @Mapping(target = "totalAmount", qualifiedBy = MobileFormat.class, source = "totalAmount")
    @Mapping(target = "createdAt",   source = "createdAt", dateFormat = "dd.MM.yyyy")
    @Mapping(target = "lineCount",   expression = "java(order.getLines().size())")
    OrderResponseDTO toMobileDto(Order order);

    // Metody z custom qualifiers — MapStruct wybiera je na podstawie adnotacji
    @WebFormat
    default String formatForWeb(BigDecimal amount) {
        if (amount == null) return "—";
        return String.format("%,.2f PLN", amount);
    }

    @MobileFormat
    default String formatForMobile(BigDecimal amount) {
        if (amount == null) return "—";
        // Skrócony format dla mobile
        if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            return String.format("%.1f tys. PLN", amount.doubleValue() / 1000);
        }
        return String.format("%.2f PLN", amount);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 36: Multiple source parameters
    // Metoda mappera przyjmuje DWIE klasy źródłowe: Order i String performedBy.
    // @Mapping(source = "paramName.field") wskazuje z którego parametru pochodzi pole.
    // ─────────────────────────────────────────────────────────────────────────────

    @Mapping(target = "orderNumber",    source = "order.orderNumber")
    @Mapping(target = "status",         source = "order.status")
    @Mapping(target = "performedBy",    source = "performedBy")
    @Mapping(target = "auditTimestamp", source = "order.createdAt", dateFormat = "yyyy-MM-dd'T'HH:mm:ss")
    @Mapping(target = "currency",       source = "order.currency")
    OrderAuditDTO toAuditDto(Order order, String performedBy);

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 37: @Mapping(defaultValue) — wartość gdy pole źródłowe jest null
    // FEATURE 38: @Mapping(defaultExpression) — wyrażenie Java gdy pole jest null
    // ─────────────────────────────────────────────────────────────────────────────

    @Mapping(target = "orderNumber",    source = "order.orderNumber")
    @Mapping(target = "status",         source = "order.status")
    @Mapping(target = "performedBy",    source = "performedBy")
    @Mapping(target = "currency",       source = "order.currency",   defaultValue = "PLN")
    @Mapping(target = "auditTimestamp", source = "order.createdAt",  dateFormat = "yyyy-MM-dd'T'HH:mm:ss",
             defaultExpression = "java(LocalDateTime.now().toString())")
    OrderAuditDTO toAuditDtoWithDefaults(Order order, String performedBy);

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 39: @SubclassMapping — polimorficzne mapowanie
    // Gdy source jest PremiumOrder (podklasa Order) → mapuj do PremiumOrderResponseDTO.
    // OrderFactory.createPremiumDto() (@ObjectFactory) jest wywoływany automatycznie.
    // ─────────────────────────────────────────────────────────────────────────────

    @SubclassMapping(source = PremiumOrder.class, target = PremiumOrderResponseDTO.class)
    @Mapping(target = "status",      source = "status")
    @Mapping(target = "totalAmount", qualifiedBy = WebFormat.class, source = "totalAmount")
    @Mapping(target = "createdAt",   source = "createdAt", dateFormat = "dd.MM.yyyy HH:mm")
    @Mapping(target = "lineCount",   expression = "java(order.getLines().size())")
    OrderResponseDTO toPolymorphicDto(Order order);

    // Metoda do mapowania PremiumOrder-specific pól (wywoływana przez @SubclassMapping)
    @Mapping(target = "status",         source = "status")
    @Mapping(target = "totalAmount",    qualifiedBy = WebFormat.class, source = "totalAmount")
    @Mapping(target = "createdAt",      source = "createdAt", dateFormat = "dd.MM.yyyy HH:mm")
    @Mapping(target = "lineCount",      expression = "java(order.getLines().size())")
    @Mapping(target = "premiumBenefits",source = "premiumBenefits")
    @Mapping(target = "priorityLevel",  source = "priorityLevel")
    @Mapping(target = "tierLabel",      ignore = true)  // ustawiany przez OrderFactory.createPremiumDto()
    PremiumOrderResponseDTO toPremiumDto(PremiumOrder order);

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 40: Stream mapping
    // MapStruct generuje implementację na podstawie metody elementarnej (toWebDto).
    // ─────────────────────────────────────────────────────────────────────────────

    // @IterableMapping(qualifiedByName) disambiguuje który mapper elementarny użyć
    @IterableMapping(qualifiedByName = "toWebDto")
    Stream<OrderResponseDTO> toWebDtoStream(Stream<Order> orders);

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 41: @EnumMapping z nameTransformationStrategy = "strip_prefix"
    // ExternalProductType.EXT_ELECTRONICS → ProductCategory.ELECTRONICS
    // ─────────────────────────────────────────────────────────────────────────────

    @EnumMapping(nameTransformationStrategy = MappingConstants.STRIP_PREFIX_TRANSFORMATION,
                 configuration = "EXT_")
    ProductCategory toProductCategory(ExternalProductType externalType);

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 42: Date splitting — jedno pole LocalDateTime → wiele pól String
    // Każde @Mapping może użyć innego dateFormat na tym samym polu źródłowym.
    // ─────────────────────────────────────────────────────────────────────────────

    @Mapping(target = "orderNumber",  source = "orderNumber")
    @Mapping(target = "status",       source = "status")
    @Mapping(target = "createdDate",  source = "createdAt",   dateFormat = "dd.MM.yyyy")
    @Mapping(target = "createdTime",  source = "createdAt",   dateFormat = "HH:mm:ss")
    @Mapping(target = "updatedDate",  source = "updatedAt",   dateFormat = "dd.MM.yyyy")
    @Mapping(target = "updatedTime",  source = "updatedAt",   dateFormat = "HH:mm:ss")
    @Mapping(target = "isoTimestamp", source = "createdAt",   dateFormat = "yyyy-MM-dd'T'HH:mm:ss")
    OrderTimelineDTO toTimelineDto(Order order);
}
