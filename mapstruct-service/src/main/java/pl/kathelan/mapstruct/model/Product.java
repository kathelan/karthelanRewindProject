package pl.kathelan.mapstruct.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private Long id;
    private String sku;
    private String name;
    private String description;
    private ProductCategory category;
    private BigDecimal pricePerUnit;
    private Unit unit;
    private Double weightKg;
    private boolean available;
}