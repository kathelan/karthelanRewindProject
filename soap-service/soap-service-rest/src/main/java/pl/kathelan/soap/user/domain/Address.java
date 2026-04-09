package pl.kathelan.soap.user.domain;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class Address {
    String street;
    String city;
    String zipCode;
    String country;
}
