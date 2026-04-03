package pl.kathelan.mapstruct.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDTO {

    private String orderNumber;
    private String customerName;
    private String status;
    private String totalAmount;
    private String createdAt;
    private int lineCount;
}
