package pl.kathelan.mapstruct.mapper;

import org.mapstruct.*;
import pl.kathelan.mapstruct.external.*;
import pl.kathelan.mapstruct.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Demonstruje mapowanie z zewnętrznego systemu (inne nazwy pól, typy, format dat, enum mapping).
 */
@Mapper(config = MapStructConfig.class, uses = {AddressMapper.class})
public interface ExternalOrderMapper {

    DateTimeFormatter EXTERNAL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // FEATURE 16: Mapowanie z całkowicie różnymi nazwami pól + dateFormat jako expression
    // FEATURE 17: Multiple field renaming w jednym mapperze
    @Mapping(target = "orderNumber",     source = "externalId")
    @Mapping(target = "notes",           source = "remarks")
    @Mapping(target = "shippingAddress", source = "deliveryAddress")   // AddressMapper.fromExternal użyty automatycznie
    @Mapping(target = "customer",        source = "clientInfo")
    @Mapping(target = "lines",           source = "items")
    @Mapping(target = "status",          source = "orderStatus")
    @Mapping(target = "createdAt",       expression = "java(parseTimestamp(payload.getOrderTimestamp()))")
    @Mapping(target = "id",              ignore = true)
    @Mapping(target = "updatedAt",       ignore = true)
    @Mapping(target = "totalAmount",     ignore = true)   // obliczane przez serwis po mapowaniu
    @Mapping(target = "currency",        constant = "PLN")  // FEATURE 18: @Mapping(constant) — stała wartość
    Order toOrder(ExternalOrderPayload payload);

    // FEATURE 19: @ValueMapping — mapowanie enum z różnymi nazwami stałych
    @ValueMapping(source = "NEW",         target = "PENDING")
    @ValueMapping(source = "APPROVED",    target = "CONFIRMED")
    @ValueMapping(source = "IN_PROGRESS", target = "PROCESSING")
    @ValueMapping(source = "IN_TRANSIT",  target = "SHIPPED")
    @ValueMapping(source = "DONE",        target = "DELIVERED")
    @ValueMapping(source = "REJECTED",    target = "CANCELLED")
    OrderStatus toOrderStatus(ExternalStatus externalStatus);

    // FEATURE 20: Odwrócenie @ValueMapping
    @InheritInverseConfiguration
    ExternalStatus toExternalStatus(OrderStatus status);

    // FEATURE 21: Mapowanie zagnieżdżonego obiektu z różnymi typami (String customerId → Long id)
    // fullName (String) → firstName + lastName — rozdzielamy w @AfterMapping
    @Mapping(target = "id",           expression = "java(parseCustomerId(info.getCustomerId()))")
    @Mapping(target = "email",        source = "emailAddress")
    @Mapping(target = "phoneNumber",  source = "phone")
    @Mapping(target = "firstName",    ignore = true)   // uzupełniany przez @AfterMapping
    @Mapping(target = "lastName",     ignore = true)   // uzupełniany przez @AfterMapping
    @Mapping(target = "billingAddress",  ignore = true)
    @Mapping(target = "shippingAddress", ignore = true)
    Customer toCustomer(ExternalCustomerInfo info);

    // FEATURE 22: @AfterMapping do rozdzielenia fullName na firstName + lastName
    @AfterMapping
    default void splitFullName(ExternalCustomerInfo source, @MappingTarget Customer target) {
        String fullName = source.getFullName();
        if (fullName != null && fullName.contains(" ")) {
            int spaceIdx = fullName.indexOf(' ');
            target.setFirstName(fullName.substring(0, spaceIdx));
            target.setLastName(fullName.substring(spaceIdx + 1));
        } else {
            target.setFirstName(fullName);
            target.setLastName("");
        }
    }

    // FEATURE 23: Mapowanie ExternalOrderLine → OrderLine z konwersją typów (String → BigDecimal)
    @Mapping(target = "product",         ignore = true)   // produkt wymaga lookup po productCode — obsługiwane przez serwis
    @Mapping(target = "id",              ignore = true)
    @Mapping(target = "lineTotal",       ignore = true)
    @Mapping(target = "quantity",        source = "qty")
    @Mapping(target = "unitPrice",       expression = "java(parseMoney(line.getPrice()))")
    @Mapping(target = "discountPercent", expression = "java(parseMoney(line.getDiscount()))")
    OrderLine toOrderLine(ExternalOrderLine line);

    List<OrderLine> toOrderLines(List<ExternalOrderLine> lines);

    // Metody pomocnicze

    default LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) return null;
        return LocalDateTime.parse(timestamp, EXTERNAL_DATE_FORMAT);
    }

    default Long parseCustomerId(String customerId) {
        if (customerId == null) return null;
        try { return Long.parseLong(customerId); } catch (NumberFormatException e) { return null; }
    }

    default BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.trim()); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }
}
