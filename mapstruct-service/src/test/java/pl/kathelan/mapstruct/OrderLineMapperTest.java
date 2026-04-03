package pl.kathelan.mapstruct;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.kathelan.mapstruct.dto.OrderLineDTO;
import pl.kathelan.mapstruct.mapper.OrderLineMapper;
import pl.kathelan.mapstruct.model.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MapstructServiceApplication.class)
class OrderLineMapperTest {

    @Autowired
    private OrderLineMapper mapper;

    private OrderLine buildLine(BigDecimal discount) {
        Product product = Product.builder()
                .id(1L).sku("ELEC-001").name("Laptop").description("desc")
                .category(ProductCategory.ELECTRONICS).pricePerUnit(new BigDecimal("3000"))
                .unit(Unit.PIECE).weightKg(1.5).available(true).build();

        return OrderLine.builder()
                .id(1L)
                .product(product)
                .quantity(2)
                .unitPrice(new BigDecimal("2999.00"))
                .discountPercent(discount)
                .lineTotal(new BigDecimal("5998.00"))
                .build();
    }

    // FEATURE 11: Nested property mapping
    @Test
    @DisplayName("FEATURE 11: product.sku → productSku, product.name → productName (nested property)")
    void toDto_shouldMapNestedProductFields() {
        OrderLineDTO dto = mapper.toDto(buildLine(null));

        assertThat(dto.getProductSku()).isEqualTo("ELEC-001");
        assertThat(dto.getProductName()).isEqualTo("Laptop");
        assertThat(dto.getQuantity()).isEqualTo(2);
    }

    // FEATURE 12: expression dla formatowania kwot
    @Test
    @DisplayName("FEATURE 12: expression — BigDecimal unitPrice/lineTotal → sformatowany String z PLN")
    void toDto_shouldFormatMoneyAmounts() {
        OrderLineDTO dto = mapper.toDto(buildLine(null));

        assertThat(dto.getUnitPriceFormatted()).contains("PLN");
        assertThat(dto.getLineTotalFormatted()).contains("PLN");
    }

    // FEATURE 13: @IterableMapping
    @Test
    @DisplayName("FEATURE 13: @IterableMapping — List<OrderLine> → List<OrderLineDTO>")
    void toDtoList_shouldMapAllElements() {
        List<OrderLineDTO> dtos = mapper.toDtoList(List.of(buildLine(null), buildLine(new BigDecimal("10"))));

        assertThat(dtos).hasSize(2);
    }

    // FEATURE 14: @AfterMapping — discountPercent ustawiany tylko gdy > 0
    @Test
    @DisplayName("FEATURE 14: @AfterMapping — discountPercent ustawiany tylko gdy > 0")
    void toDto_withPositiveDiscount_shouldSetFormattedDiscount() {
        OrderLineDTO dto = mapper.toDto(buildLine(new BigDecimal("15")));

        assertThat(dto.getDiscountPercent()).isEqualTo("15%");
    }

    @Test
    @DisplayName("FEATURE 14: @AfterMapping — discountPercent null gdy brak rabatu")
    void toDto_withNullDiscount_shouldLeaveDiscountNull() {
        OrderLineDTO dto = mapper.toDto(buildLine(null));

        assertThat(dto.getDiscountPercent()).isNull();
    }

    @Test
    @DisplayName("FEATURE 14: @AfterMapping — discountPercent null gdy discount = 0")
    void toDto_withZeroDiscount_shouldLeaveDiscountNull() {
        OrderLineDTO dto = mapper.toDto(buildLine(BigDecimal.ZERO));

        assertThat(dto.getDiscountPercent()).isNull();
    }

    // FEATURE 15: @Condition
    @Test
    @DisplayName("FEATURE 15: @Condition — isPositiveDiscount zwraca false dla null i 0")
    void condition_shouldRejectNullAndZeroDiscount() {
        assertThat(mapper.isPositiveDiscount(null)).isFalse();
        assertThat(mapper.isPositiveDiscount(BigDecimal.ZERO)).isFalse();
        assertThat(mapper.isPositiveDiscount(new BigDecimal("0.01"))).isTrue();
    }
}
