package pl.kathelan.mapstruct;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.kathelan.mapstruct.dto.*;
import pl.kathelan.mapstruct.mapper.OrderMapper;
import pl.kathelan.mapstruct.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = MapstructServiceApplication.class)
class OrderMapperTest {

    @Autowired
    private OrderMapper mapper;

    private Order buildOrder() {
        Address address = Address.builder()
                .street("Marszałkowska").buildingNumber("10").city("Warszawa")
                .zipCode("00-001").country("Polska").build();

        Customer customer = Customer.builder()
                .id(1L).firstName("Jan").lastName("Kowalski")
                .email("jan@example.com").phoneNumber("+48500000000").build();

        Product product = Product.builder()
                .id(1L).sku("ELEC-001").name("Laptop").description("desc")
                .category(ProductCategory.ELECTRONICS).pricePerUnit(new BigDecimal("2999"))
                .unit(Unit.PIECE).weightKg(1.5).available(true).build();

        OrderLine line = OrderLine.builder()
                .id(1L).product(product).quantity(1)
                .unitPrice(new BigDecimal("2999.00"))
                .discountPercent(null)
                .lineTotal(new BigDecimal("2999.00")).build();

        return Order.builder()
                .id(100L)
                .orderNumber("ORD-2024-001")
                .customer(customer)
                .lines(List.of(line))
                .status(OrderStatus.CONFIRMED)
                .createdAt(LocalDateTime.of(2024, 3, 15, 10, 30))
                .updatedAt(LocalDateTime.now())
                .shippingAddress(address)
                .notes("Dostarczyć rano")
                .totalAmount(new BigDecimal("2999.00"))
                .currency("PLN")
                .build();
    }

    // FEATURE 24: dateFormat
    @Test
    @DisplayName("FEATURE 24: dateFormat — LocalDateTime → String 'dd.MM.yyyy HH:mm'")
    void toResponseDto_shouldFormatDate() {
        OrderResponseDTO dto = mapper.toResponseDto(buildOrder());

        assertThat(dto.getCreatedAt()).isEqualTo("15.03.2024 10:30");
    }

    // FEATURE 25: numberFormat
    @Test
    @DisplayName("FEATURE 25: numberFormat — BigDecimal → String '#,##0.00 PLN'")
    void toResponseDto_shouldFormatTotalAmount() {
        OrderResponseDTO dto = mapper.toResponseDto(buildOrder());

        assertThat(dto.getTotalAmount()).contains("PLN");
        assertThat(dto.getTotalAmount()).contains("2");
    }

    // FEATURE 26: expression dla lineCount
    @Test
    @DisplayName("FEATURE 26: expression — lineCount = order.getLines().size()")
    void toResponseDto_shouldComputeLineCount() {
        OrderResponseDTO dto = mapper.toResponseDto(buildOrder());

        assertThat(dto.getLineCount()).isEqualTo(1);
    }

    // FEATURE 27: @InheritConfiguration
    @Test
    @DisplayName("FEATURE 27: @InheritConfiguration — toResponseDtoMinimal dziedziczy dateFormat i numberFormat")
    void toResponseDtoMinimal_shouldInheritFormats() {
        OrderResponseDTO dto = mapper.toResponseDtoMinimal(buildOrder());

        // dateFormat i numberFormat odziedziczone
        assertThat(dto.getCreatedAt()).isEqualTo("15.03.2024 10:30");
        assertThat(dto.getTotalAmount()).contains("PLN");
        // pola pominięte (ignore = true)
        assertThat(dto.getCustomer()).isNull();
        assertThat(dto.getLines()).isNull();
    }

    // FEATURE 28: OrderSummaryDTO — uproszczony widok
    @Test
    @DisplayName("FEATURE 28: toSummaryDto — uproszczony widok z customerName i lineCount")
    void toSummaryDto_shouldBuildSimplifiedView() {
        OrderSummaryDTO dto = mapper.toSummaryDto(buildOrder());

        assertThat(dto.getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(dto.getCustomerName()).isEqualTo("Jan Kowalski");
        assertThat(dto.getLineCount()).isEqualTo(1);
        assertThat(dto.getCreatedAt()).isEqualTo("15.03.2024"); // krótszy format
    }

    // FEATURE 29: @MappingTarget — aktualizacja istniejącego obiektu
    @Test
    @DisplayName("FEATURE 29: @MappingTarget — updateOrder aktualizuje tylko niepuste pola (PATCH)")
    void updateOrder_shouldMutateExistingOrder() {
        Order order = buildOrder();
        String originalNumber = order.getOrderNumber();
        LocalDateTime originalCreatedAt = order.getCreatedAt();

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setNotes("Zmieniona notatka");
        request.setStatus("SHIPPED");

        mapper.updateOrder(request, order);

        // zaktualizowane
        assertThat(order.getNotes()).isEqualTo("Zmieniona notatka");
        // niezmienione (ignore w mapperze)
        assertThat(order.getOrderNumber()).isEqualTo(originalNumber);
        assertThat(order.getCreatedAt()).isEqualTo(originalCreatedAt);
        // updatedAt ustawiony przez expression = "java(LocalDateTime.now())"
        assertThat(order.getUpdatedAt()).isNotNull();
    }

    // FEATURE 29: nullValuePropertyMappingStrategy = IGNORE — null pola nie nadpisują
    @Test
    @DisplayName("FEATURE 29: NullValuePropertyMappingStrategy.IGNORE — null request.notes nie nadpisuje")
    void updateOrder_withNullField_shouldNotOverwriteExistingValue() {
        Order order = buildOrder();
        order.setNotes("Oryginalna notatka");

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setNotes(null);   // null — nie powinno nadpisać
        request.setStatus("SHIPPED");

        mapper.updateOrder(request, order);

        assertThat(order.getNotes()).isEqualTo("Oryginalna notatka");
    }

    // FEATURE 30: @BeforeMapping — walidacja
    @Test
    @DisplayName("FEATURE 30: @BeforeMapping — rzuca wyjątek gdy Order ma pustą listę linii")
    void toResponseDto_withEmptyLines_shouldThrowBeforeMapping() {
        Order order = buildOrder();
        order.setLines(List.of());

        assertThatThrownBy(() -> mapper.toResponseDto(order))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one line");
    }

    // FEATURE 31: @AfterMapping
    @Test
    @DisplayName("FEATURE 31: @AfterMapping — enrichResponseDto ustawia lineCount po mapowaniu")
    void toResponseDto_afterMapping_shouldSetLineCount() {
        Order order = buildOrder();

        OrderResponseDTO dto = mapper.toResponseDto(order);

        assertThat(dto.getLineCount()).isGreaterThan(0);
    }

    // FEATURE 32 & 33: @Named + Decorator — status zamieniony na polską etykietę
    @Test
    @DisplayName("FEATURE 32+33: @Named statusToLabel + @DecoratedWith — status jako polska etykieta")
    void toResponseDto_shouldReturnPolishStatusLabel() {
        OrderResponseDTO dto = mapper.toResponseDto(buildOrder()); // CONFIRMED

        assertThat(dto.getStatus()).isEqualTo("Potwierdzone");
    }

    @Test
    @DisplayName("FEATURE 33: Decorator — toSummaryDto też podmienia status na polską etykietę")
    void toSummaryDto_shouldReturnPolishStatusLabel() {
        OrderSummaryDTO dto = mapper.toSummaryDto(buildOrder()); // CONFIRMED

        assertThat(dto.getStatus()).isEqualTo("Potwierdzone");
    }

    @Test
    @DisplayName("FEATURE 32: statusToLabel — wszystkie wartości enum mają polskie etykiety")
    void statusToLabel_shouldReturnPolishLabels() {
        assertThat(mapper.statusToLabel(OrderStatus.PENDING)).isEqualTo("Oczekujące");
        assertThat(mapper.statusToLabel(OrderStatus.SHIPPED)).isEqualTo("Wysłane");
        assertThat(mapper.statusToLabel(OrderStatus.DELIVERED)).isEqualTo("Dostarczone");
        assertThat(mapper.statusToLabel(OrderStatus.CANCELLED)).isEqualTo("Anulowane");
    }
}
