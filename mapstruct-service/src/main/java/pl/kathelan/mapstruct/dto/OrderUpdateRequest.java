package pl.kathelan.mapstruct.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdateRequest {

    private String status;
    private String notes;
    private AddressDTO shippingAddress;
}
