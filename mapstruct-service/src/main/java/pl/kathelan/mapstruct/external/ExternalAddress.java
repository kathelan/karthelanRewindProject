package pl.kathelan.mapstruct.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalAddress {

    private String streetLine;
    private String buildingNo;
    private String cityName;
    private String postalCode;
    private String countryCode;
}
