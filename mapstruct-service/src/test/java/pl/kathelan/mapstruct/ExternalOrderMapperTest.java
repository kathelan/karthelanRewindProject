package pl.kathelan.mapstruct;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.kathelan.mapstruct.external.*;
import pl.kathelan.mapstruct.mapper.ExternalOrderMapper;
import pl.kathelan.mapstruct.model.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MapstructServiceApplication.class)
class ExternalOrderMapperTest {

    @Autowired
    private ExternalOrderMapper mapper;

    private ExternalOrderPayload buildPayload() {
        ExternalCustomerInfo client = new ExternalCustomerInfo("42", "Jan Kowalski", "jan@example.com", "+48500000000");
        ExternalAddress addr = new ExternalAddress("Marszałkowska", "10", "Warszawa", "00-001", "PL");
        ExternalOrderLine line = new ExternalOrderLine("ELEC-001", "Laptop", 1, "2999.00", null);

        ExternalOrderPayload payload = new ExternalOrderPayload();
        payload.setExternalId("EXT-100");
        payload.setOrderStatus(ExternalStatus.NEW);
        payload.setClientInfo(client);
        payload.setDeliveryAddress(addr);
        payload.setItems(List.of(line));
        payload.setOrderTimestamp("2024-01-15 10:30:00");
        payload.setRemarks("Pilne");
        return payload;
    }

    // FEATURE 16 & 17: Mapowanie z różnymi nazwami pól
    @Test
    @DisplayName("FEATURE 16/17: externalId→orderNumber, remarks→notes, różne nazwy pól")
    void toOrder_shouldMapRenamedFields() {
        Order order = mapper.toOrder(buildPayload());

        assertThat(order.getOrderNumber()).isEqualTo("EXT-100");
        assertThat(order.getNotes()).isEqualTo("Pilne");
    }

    // FEATURE 18: constant = "PLN"
    @Test
    @DisplayName("FEATURE 18: @Mapping(constant) — currency zawsze 'PLN'")
    void toOrder_shouldSetConstantCurrency() {
        Order order = mapper.toOrder(buildPayload());

        assertThat(order.getCurrency()).isEqualTo("PLN");
    }

    // FEATURE 19: @ValueMapping — wszystkie wartości ExternalStatus → OrderStatus
    @Test
    @DisplayName("FEATURE 19: @ValueMapping NEW → PENDING")
    void toOrderStatus_NEW_shouldBePENDING() {
        assertThat(mapper.toOrderStatus(ExternalStatus.NEW)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("FEATURE 19: @ValueMapping DONE → DELIVERED")
    void toOrderStatus_DONE_shouldBeDELIVERED() {
        assertThat(mapper.toOrderStatus(ExternalStatus.DONE)).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("FEATURE 19: @ValueMapping REJECTED → CANCELLED")
    void toOrderStatus_REJECTED_shouldBeCANCELLED() {
        assertThat(mapper.toOrderStatus(ExternalStatus.REJECTED)).isEqualTo(OrderStatus.CANCELLED);
    }

    // FEATURE 20: @InheritInverseConfiguration na enumach
    @Test
    @DisplayName("FEATURE 20: @InheritInverseConfiguration — OrderStatus → ExternalStatus odwrotnie")
    void toExternalStatus_shouldReverseMap() {
        assertThat(mapper.toExternalStatus(OrderStatus.PENDING)).isEqualTo(ExternalStatus.NEW);
        assertThat(mapper.toExternalStatus(OrderStatus.DELIVERED)).isEqualTo(ExternalStatus.DONE);
        assertThat(mapper.toExternalStatus(OrderStatus.CANCELLED)).isEqualTo(ExternalStatus.REJECTED);
    }

    // FEATURE 21: String customerId → Long id
    @Test
    @DisplayName("FEATURE 21: String customerId → Long id (konwersja typów przez expression)")
    void toOrder_shouldConvertCustomerIdToLong() {
        Order order = mapper.toOrder(buildPayload());

        assertThat(order.getCustomer()).isNotNull();
        assertThat(order.getCustomer().getId()).isEqualTo(42L);
    }

    // FEATURE 22: @AfterMapping splitFullName
    @Test
    @DisplayName("FEATURE 22: @AfterMapping splitFullName — 'Jan Kowalski' → firstName='Jan', lastName='Kowalski'")
    void toOrder_shouldSplitFullName() {
        Order order = mapper.toOrder(buildPayload());

        assertThat(order.getCustomer().getFirstName()).isEqualTo("Jan");
        assertThat(order.getCustomer().getLastName()).isEqualTo("Kowalski");
    }

    // FEATURE 23: String price → BigDecimal
    @Test
    @DisplayName("FEATURE 23: String price → BigDecimal (konwersja przez parseMoney)")
    void toOrder_shouldParseMoneyFromString() {
        Order order = mapper.toOrder(buildPayload());

        assertThat(order.getLines()).hasSize(1);
        assertThat(order.getLines().get(0).getUnitPrice()).isEqualByComparingTo("2999.00");
    }

    // dateFormat: orderTimestamp string → LocalDateTime
    @Test
    @DisplayName("orderTimestamp String → LocalDateTime (parseTimestamp)")
    void toOrder_shouldParseTimestamp() {
        Order order = mapper.toOrder(buildPayload());

        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getCreatedAt().getYear()).isEqualTo(2024);
        assertThat(order.getCreatedAt().getMonthValue()).isEqualTo(1);
        assertThat(order.getCreatedAt().getDayOfMonth()).isEqualTo(15);
    }
}
