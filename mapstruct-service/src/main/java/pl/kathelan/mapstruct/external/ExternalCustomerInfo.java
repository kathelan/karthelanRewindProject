package pl.kathelan.mapstruct.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalCustomerInfo {

    private String customerId;
    private String fullName;
    private String emailAddress;
    private String phone;
}
