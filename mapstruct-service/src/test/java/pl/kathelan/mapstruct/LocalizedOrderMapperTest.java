package pl.kathelan.mapstruct;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.kathelan.mapstruct.config.CurrencyContext;
import pl.kathelan.mapstruct.dto.OrderAuditDTO;
import pl.kathelan.mapstruct.dto.OrderResponseDTO;
import pl.kathelan.mapstruct.dto.OrderTimelineDTO;
import pl.kathelan.mapstruct.dto.PremiumOrderResponseDTO;
import pl.kathelan.mapstruct.external.ExternalProductType;
import pl.kathelan.mapstruct.mapper.LocalizedOrderMapper;
import pl.kathelan.mapstruct.model.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MapstructServiceApplication.class)
class LocalizedOrderMapperTest {

    @Autowired
    private LocalizedOrderMapper mapper;

    private Order buildOrder() {
        return Order.builder()
                .id(1L)
                .orderNumber("ORD-2024-001")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("1500.00"))
                .currency("PLN")
                .createdAt(LocalDateTime.of(2024, 6, 15, 14, 30, 0))
                .updatedAt(LocalDateTime.of(2024, 6, 16, 9, 0, 0))
                .lines(List.of())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 34: @Context
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F34: @Context — formatAmount używa CurrencyContext.polish()")
    void toLocalizedDto_withPolishContext_formatsAmountInPolish() {
        Order order = buildOrder();
        CurrencyContext ctx = CurrencyContext.polish();

        OrderResponseDTO dto = mapper.toLocalizedDto(order, ctx);

        assertThat(dto.getTotalAmount()).contains("zł");   // Locale("pl","PL") → symbol "zł", nie "PLN"
        assertThat(dto.getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(dto.getLineCount()).isZero();
    }

    @Test
    @DisplayName("F34: @Context — formatAmount używa CurrencyContext.euro()")
    void toLocalizedDto_withEuroContext_formatsAmountInEuro() {
        Order order = buildOrder();
        CurrencyContext ctx = CurrencyContext.euro();

        OrderResponseDTO dto = mapper.toLocalizedDto(order, ctx);

        assertThat(dto.getTotalAmount()).contains("€");   // Locale.GERMANY → symbol "€", nie "EUR"
    }

    @Test
    @DisplayName("F34: @Context — null totalAmount zwraca '—'")
    void toLocalizedDto_nullAmount_returnsEmDash() {
        Order order = buildOrder();
        order.setTotalAmount(null);

        OrderResponseDTO dto = mapper.toLocalizedDto(order, CurrencyContext.polish());

        assertThat(dto.getTotalAmount()).isEqualTo("—");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 35: Custom @Qualifier
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F35: @WebFormat — kwota >= 1000 z przecinkiem i 'PLN'")
    void toWebDto_formatsAmountWithWebQualifier() {
        Order order = buildOrder();

        OrderResponseDTO dto = mapper.toWebDto(order);

        assertThat(dto.getTotalAmount()).isEqualTo("1\u00a0500,00 PLN");
    }

    @Test
    @DisplayName("F35: @MobileFormat — kwota >= 1000 skrócona do 'X.X tys. PLN'")
    void toMobileDto_formatsLargeAmountAsThousands() {
        Order order = buildOrder();

        OrderResponseDTO dto = mapper.toMobileDto(order);

        assertThat(dto.getTotalAmount()).isEqualTo("1,5 tys. PLN");
    }

    @Test
    @DisplayName("F35: @MobileFormat — kwota < 1000 z pełnym formatem")
    void toMobileDto_formatsSmallAmountFull() {
        Order order = buildOrder();
        order.setTotalAmount(new BigDecimal("99.50"));

        OrderResponseDTO dto = mapper.toMobileDto(order);

        assertThat(dto.getTotalAmount()).isEqualTo("99,50 PLN");
    }

    @Test
    @DisplayName("F35: @WebFormat/@MobileFormat — null source nie wywołuje qualifiera, totalAmount jest null")
    void toWebAndMobileDto_nullAmount_totalAmountIsNull() {
        // MapStruct nie wywołuje qualified method gdy source jest null — pole target pozostaje null
        Order order = buildOrder();
        order.setTotalAmount(null);

        assertThat(mapper.toWebDto(order).getTotalAmount()).isNull();
        assertThat(mapper.toMobileDto(order).getTotalAmount()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 36: Multiple source parameters
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F36: Multiple source params — performedBy pochodzi z drugiego argumentu")
    void toAuditDto_mapsPerformedByFromSecondParam() {
        Order order = buildOrder();

        OrderAuditDTO dto = mapper.toAuditDto(order, "admin@example.com");

        assertThat(dto.getPerformedBy()).isEqualTo("admin@example.com");
        assertThat(dto.getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(dto.getStatus()).isEqualTo("CONFIRMED");
        assertThat(dto.getAuditTimestamp()).isEqualTo("2024-06-15T14:30:00");
        assertThat(dto.getCurrency()).isEqualTo("PLN");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 37: defaultValue  /  FEATURE 38: defaultExpression
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F37: defaultValue — null currency zastępowane przez 'PLN'")
    void toAuditDtoWithDefaults_nullCurrency_usesDefaultValue() {
        Order order = buildOrder();
        order.setCurrency(null);

        OrderAuditDTO dto = mapper.toAuditDtoWithDefaults(order, "user");

        assertThat(dto.getCurrency()).isEqualTo("PLN");
    }

    @Test
    @DisplayName("F38: defaultExpression — null createdAt zastępowane przez LocalDateTime.now()")
    void toAuditDtoWithDefaults_nullCreatedAt_usesDefaultExpression() {
        Order order = buildOrder();
        order.setCreatedAt(null);

        OrderAuditDTO dto = mapper.toAuditDtoWithDefaults(order, "user");

        assertThat(dto.getAuditTimestamp()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("F37+F38: niepuste pola nie są nadpisywane defaultami")
    void toAuditDtoWithDefaults_presentFields_notOverriddenByDefaults() {
        Order order = buildOrder();

        OrderAuditDTO dto = mapper.toAuditDtoWithDefaults(order, "user");

        assertThat(dto.getCurrency()).isEqualTo("PLN");
        assertThat(dto.getAuditTimestamp()).isEqualTo("2024-06-15T14:30:00");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 39: @SubclassMapping + @ObjectFactory
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F39: @SubclassMapping — PremiumOrder → PremiumOrderResponseDTO z polami premium")
    void toPolymorphicDto_withPremiumOrder_returnsPremiumResponseDTO() {
        PremiumOrder premium = PremiumOrder.builder()
                .id(2L)
                .orderNumber("ORD-PREM-001")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("5000.00"))
                .createdAt(LocalDateTime.of(2024, 6, 15, 14, 30, 0))
                .lines(List.of())
                .premiumBenefits("Darmowa dostawa")
                .priorityLevel(3)
                .build();

        OrderResponseDTO dto = mapper.toPolymorphicDto(premium);

        assertThat(dto).isInstanceOf(PremiumOrderResponseDTO.class);
        PremiumOrderResponseDTO premiumDto = (PremiumOrderResponseDTO) dto;
        assertThat(premiumDto.getPremiumBenefits()).isEqualTo("Darmowa dostawa");
        assertThat(premiumDto.getPriorityLevel()).isEqualTo(3);
        // UWAGA: @ObjectFactory jest pomijany gdy klasa ma @SuperBuilder —
        // MapStruct używa builder() zamiast factory, tierLabel = null
        assertThat(premiumDto.getTierLabel()).isNull();
    }

    @Test
    @DisplayName("F39: @ObjectFactory — nie wywoływany gdy target ma @SuperBuilder (MapStruct preferuje builder)")
    void toPolymorphicDto_objectFactoryBypassedBySuperBuilder() {
        // @ObjectFactory w OrderFactory.createPremiumDto() nie jest wywoływany
        // bo PremiumOrderResponseDTO/@SuperBuilder generuje kod z .builder() zamiast new + factory
        PremiumOrder order = PremiumOrder.builder()
                .id(3L).orderNumber("ORD-002").status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.TEN).createdAt(LocalDateTime.now())
                .lines(List.of()).premiumBenefits("").priorityLevel(2).build();

        PremiumOrderResponseDTO dto = (PremiumOrderResponseDTO) mapper.toPolymorphicDto(order);

        assertThat(dto.getTierLabel()).isNull();
    }

    @Test
    @DisplayName("F39: Order (nie PremiumOrder) → OrderResponseDTO (nie Premium)")
    void toPolymorphicDto_withRegularOrder_returnsOrderResponseDTO() {
        Order order = buildOrder();

        OrderResponseDTO dto = mapper.toPolymorphicDto(order);

        assertThat(dto.getClass()).isEqualTo(OrderResponseDTO.class);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 40: Stream mapping
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F40: Stream mapping — każdy Order w strumieniu jest mapowany przez toWebDto")
    void toWebDtoStream_mapsAllOrdersInStream() {
        Order o1 = buildOrder();
        Order o2 = buildOrder();
        o2.setOrderNumber("ORD-2024-002");
        o2.setTotalAmount(new BigDecimal("250.00"));

        List<OrderResponseDTO> results = mapper.toWebDtoStream(Stream.of(o1, o2)).toList();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(results.get(1).getOrderNumber()).isEqualTo("ORD-2024-002");
        assertThat(results.get(0).getTotalAmount()).contains("PLN");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 41: @EnumMapping strip_prefix
    // ─────────────────────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → bez prefiksu EXT_")
    @EnumSource(ExternalProductType.class)
    @DisplayName("F41: @EnumMapping — EXT_ prefix usuwany z każdej wartości")
    void toProductCategory_stripsExtPrefix(ExternalProductType externalType) {
        String expectedName = externalType.name().substring("EXT_".length());
        ProductCategory result = mapper.toProductCategory(externalType);
        assertThat(result.name()).isEqualTo(expectedName);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // FEATURE 42: Date splitting
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("F42: Date splitting — LocalDateTime rozbite na osobne pola daty i czasu")
    void toTimelineDto_splitsDateTimeFields() {
        Order order = buildOrder();

        OrderTimelineDTO dto = mapper.toTimelineDto(order);

        assertThat(dto.getOrderNumber()).isEqualTo("ORD-2024-001");
        assertThat(dto.getStatus()).isEqualTo("CONFIRMED");
        assertThat(dto.getCreatedDate()).isEqualTo("15.06.2024");
        assertThat(dto.getCreatedTime()).isEqualTo("14:30:00");
        assertThat(dto.getUpdatedDate()).isEqualTo("16.06.2024");
        assertThat(dto.getUpdatedTime()).isEqualTo("09:00:00");
        assertThat(dto.getIsoTimestamp()).isEqualTo("2024-06-15T14:30:00");
    }

    @Test
    @DisplayName("F42: Date splitting — null updatedAt daje null w polach updatedDate/updatedTime")
    void toTimelineDto_nullUpdatedAt_produceNullFields() {
        Order order = buildOrder();
        order.setUpdatedAt(null);

        OrderTimelineDTO dto = mapper.toTimelineDto(order);

        assertThat(dto.getUpdatedDate()).isNull();
        assertThat(dto.getUpdatedTime()).isNull();
        assertThat(dto.getCreatedDate()).isNotNull();
    }

    @Test
    @DisplayName("F42: Date splitting — null createdAt daje null w createdDate/createdTime/isoTimestamp")
    void toTimelineDto_nullCreatedAt_produceNullCreatedFields() {
        Order order = buildOrder();
        order.setCreatedAt(null);

        OrderTimelineDTO dto = mapper.toTimelineDto(order);

        assertThat(dto.getCreatedDate()).isNull();
        assertThat(dto.getCreatedTime()).isNull();
        assertThat(dto.getIsoTimestamp()).isNull();
        assertThat(dto.getUpdatedDate()).isNotNull();
    }
}