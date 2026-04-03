package pl.kathelan.mapstruct.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDTO {

    private Long id;
    private String orderNumber;
    private CustomerDTO customer;
    private List<OrderLineDTO> lines;
    private String status;
    private String createdAt;
    private AddressDTO shippingAddress;
    private String notes;
    private String totalAmount;
    private String currency;
    private int lineCount;
}
