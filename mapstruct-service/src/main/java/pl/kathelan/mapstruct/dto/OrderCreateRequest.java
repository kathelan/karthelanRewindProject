package pl.kathelan.mapstruct.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    private Long customerId;
    private List<OrderLineRequest> lines;
    private AddressDTO shippingAddress;
    private String notes;
    private String currency;
}
