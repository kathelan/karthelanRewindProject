package pl.kathelan.mapstruct.mapper;

import org.mapstruct.*;
import pl.kathelan.mapstruct.dto.ProductDTO;
import pl.kathelan.mapstruct.model.Product;

import java.math.BigDecimal;
import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface ProductMapper {

    // FEATURE 5: enum → String (automatyczne), ignore pola bez odpowiednika w DTO
    // FEATURE 6: expression = "java(...)" do formatowania ceny
    @Mapping(target = "category",       source = "category")        // ProductCategory → String (auto)
    @Mapping(target = "unit",           source = "unit")            // Unit → String (auto)
    @Mapping(target = "priceFormatted", expression = "java(formatPrice(product.getPricePerUnit()))")
    ProductDTO toDto(Product product);

    // FEATURE 7: @IterableMapping — mapowanie kolekcji (metoda elementarna jest wykrywana automatycznie)
    List<ProductDTO> toDtoList(List<Product> products);

    // FEATURE 8: @Named — metoda pomocnicza z qualifierem; używana przez expression powyżej
    @Named("formatPrice")
    default String formatPrice(BigDecimal price) {
        if (price == null) return "N/A";
        return String.format("%,.2f PLN", price);
    }
}
