package pl.kathelan.mapstruct.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalOrderLine {

    private String productCode;
    private String productName;
    private int qty;
    private String price;
    private String discount;
}
