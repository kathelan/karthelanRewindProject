package pl.kathelan.mapstruct.mapper;

import org.mapstruct.*;
import pl.kathelan.mapstruct.dto.*;
import pl.kathelan.mapstruct.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Główny mapper zamówień — demonstruje zaawansowane funkcje MapStruct.
 * Używa @DecoratedWith do wzbogacenia wygenerowanego kodu.
 */
@Mapper(
    config = MapStructConfig.class,
    uses = {CustomerMapper.class, OrderLineMapper.class, AddressMapper.class},
    imports = {LocalDateTime.class}
)
@DecoratedWith(OrderMapperDecorator.class)
public interface OrderMapper {

    // FEATURE 24: dateFormat — automatyczna konwersja LocalDateTime → String
    // FEATURE 25: numberFormat — BigDecimal → String z formatem liczbowym
    // FEATURE 26: expression dla lineCount (rozmiar kolekcji)
    @Mapping(target = "status",      source = "status")       // OrderStatus → String (auto enum)
    @Mapping(target = "createdAt",   source = "createdAt",    dateFormat = "dd.MM.yyyy HH:mm")
    @Mapping(target = "totalAmount", source = "totalAmount",  numberFormat = "#,##0.00 PLN")
    @Mapping(target = "lineCount",   expression = "java(order.getLines().size())")
    OrderResponseDTO toResponseDto(Order order);

    // FEATURE 27: @InheritConfiguration — dziedziczy konfigurację z toResponseDto (dateFormat, numberFormat)
    // UWAGA: tylko wspólne mappingi są dziedziczone; nadpisujemy/dodajemy to co inne
    @InheritConfiguration(name = "toResponseDto")
    @Mapping(target = "id",             ignore = true)
    @Mapping(target = "customer",       ignore = true)
    @Mapping(target = "lines",          ignore = true)
    @Mapping(target = "shippingAddress",ignore = true)
    @Mapping(target = "notes",          ignore = true)
    @Mapping(target = "currency",       ignore = true)
    OrderResponseDTO toResponseDtoMinimal(Order order);

    // FEATURE 28: OrderSummaryDTO — uproszczony widok (mobile)
    // Pokazuje @Mapping z source na zagnieżdżone pole (customer.firstName + customer.lastName przez expression)
    @Mapping(target = "customerName",  expression = "java(order.getCustomer().getFirstName() + \" \" + order.getCustomer().getLastName())")
    @Mapping(target = "status",        source = "status")
    @Mapping(target = "totalAmount",   source = "totalAmount",  numberFormat = "#,##0.00 PLN")
    @Mapping(target = "createdAt",     source = "createdAt",    dateFormat = "dd.MM.yyyy")
    @Mapping(target = "lineCount",     expression = "java(order.getLines().size())")
    OrderSummaryDTO toSummaryDto(Order order);

    // FEATURE 29: @MappingTarget — aktualizacja istniejącego obiektu (PATCH semantics)
    // nullValuePropertyMappingStrategy = IGNORE (z MapStructConfig) — null pola są pomijane
    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "customer",    ignore = true)
    @Mapping(target = "lines",       ignore = true)
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "updatedAt",   expression = "java(LocalDateTime.now())")
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "currency",    ignore = true)
    void updateOrder(OrderUpdateRequest request, @MappingTarget Order order);

    // FEATURE 30: @BeforeMapping — walidacja/setup przed mapowaniem
    @BeforeMapping
    default void validateOrder(Order order) {
        if (order == null) throw new IllegalArgumentException("Order cannot be null");
        if (order.getLines() == null || order.getLines().isEmpty()) {
            throw new IllegalStateException("Order must have at least one line");
        }
    }

    // FEATURE 31: @AfterMapping — post-processing po mapowaniu (np. audit/obliczenia)
    @AfterMapping
    default void enrichResponseDto(Order source, @MappingTarget OrderResponseDTO target) {
        // Dodaj podsumowanie tylko gdy mamy dane
        if (source.getTotalAmount() != null && source.getCurrency() != null) {
            // lineCount już ustawiony przez expression, ale możemy tu dodać inne wzbogacenia
            target.setLineCount(source.getLines().size());
        }
    }

    // FEATURE 32: @Named qualifier — ta sama metoda dostępna pod kwalifikatorem
    // Demonstruje selekcję metody po nazwie gdy jest kilka kandydatów tego samego typu
    @Named("statusLabel")
    default String statusToLabel(OrderStatus status) {
        if (status == null) return "UNKNOWN";
        return switch (status) {
            case PENDING    -> "Oczekujące";
            case CONFIRMED  -> "Potwierdzone";
            case PROCESSING -> "W realizacji";
            case SHIPPED    -> "Wysłane";
            case DELIVERED  -> "Dostarczone";
            case CANCELLED  -> "Anulowane";
        };
    }
}
