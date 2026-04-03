package pl.kathelan.mapstruct;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.kathelan.mapstruct.dto.ProductDTO;
import pl.kathelan.mapstruct.mapper.ProductMapper;
import pl.kathelan.mapstruct.model.Product;
import pl.kathelan.mapstruct.model.ProductCategory;
import pl.kathelan.mapstruct.model.Unit;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MapstructServiceApplication.class)
class ProductMapperTest {

    @Autowired
    private ProductMapper mapper;

    private Product buildProduct() {
        return Product.builder()
                .id(1L)
                .sku("ELEC-001")
                .name("Laptop Pro")
                .description("Wydajny laptop")
                .category(ProductCategory.ELECTRONICS)
                .pricePerUnit(new BigDecimal("3499.99"))
                .unit(Unit.PIECE)
                .weightKg(1.8)
                .available(true)
                .build();
    }

    // FEATURE 5: enum → String (automatyczne)
    @Test
    @DisplayName("FEATURE 5: enum ProductCategory → String (automatyczna konwersja)")
    void toDto_shouldConvertEnumToString() {
        ProductDTO dto = mapper.toDto(buildProduct());

        assertThat(dto.getCategory()).isEqualTo("ELECTRONICS");
        assertThat(dto.getUnit()).isEqualTo("PIECE");
    }

    // FEATURE 6: expression dla formatowania ceny
    @Test
    @DisplayName("FEATURE 6: expression = java(...) — BigDecimal formatowana jako String z PLN")
    void toDto_shouldFormatPriceWithExpression() {
        ProductDTO dto = mapper.toDto(buildProduct());

        assertThat(dto.getPriceFormatted()).isNotNull();
        assertThat(dto.getPriceFormatted()).contains("PLN");
        assertThat(dto.getPriceFormatted()).contains("3");
    }

    // FEATURE 7: @IterableMapping — mapowanie listy
    @Test
    @DisplayName("FEATURE 7: List<Product> → List<ProductDTO> (automatyczne IterableMapping)")
    void toDtoList_shouldMapEachElement() {
        Product p1 = buildProduct();
        Product p2 = Product.builder()
                .id(2L)
                .sku("BOOK-001")
                .name("Java 21 in Practice")
                .category(ProductCategory.BOOKS)
                .pricePerUnit(new BigDecimal("89.90"))
                .unit(Unit.PIECE)
                .weightKg(0.5)
                .available(true)
                .build();

        List<ProductDTO> dtos = mapper.toDtoList(List.of(p1, p2));

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getSku()).isEqualTo("ELEC-001");
        assertThat(dtos.get(1).getCategory()).isEqualTo("BOOKS");
    }

    // FEATURE 8: @Named — metoda pomocnicza (wywołana przez expression)
    @Test
    @DisplayName("FEATURE 8: @Named formatPrice — null price → 'N/A'")
    void toDto_withNullPrice_shouldReturnNA() {
        Product product = Product.builder()
                .id(3L)
                .sku("X")
                .name("X")
                .category(ProductCategory.OTHER)
                .unit(Unit.PIECE)
                .pricePerUnit(null)
                .available(false)
                .build();

        ProductDTO dto = mapper.toDto(product);

        assertThat(dto.getPriceFormatted()).isEqualTo("N/A");
    }
}
