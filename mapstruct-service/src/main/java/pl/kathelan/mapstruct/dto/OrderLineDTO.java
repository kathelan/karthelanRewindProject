package pl.kathelan.mapstruct.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineDTO {

    private String productSku;
    private String productName;
    private int quantity;
    private String unitPriceFormatted;
    private String discountPercent;
    private String lineTotalFormatted;
}