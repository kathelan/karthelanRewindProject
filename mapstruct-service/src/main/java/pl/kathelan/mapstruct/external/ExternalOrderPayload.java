package pl.kathelan.mapstruct.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalOrderPayload {

    private String externalId;
    private ExternalStatus orderStatus;
    private ExternalCustomerInfo clientInfo;
    private List<ExternalOrderLine> items;
    private ExternalAddress deliveryAddress;
    private String orderTimestamp;
    private String remarks;
}
