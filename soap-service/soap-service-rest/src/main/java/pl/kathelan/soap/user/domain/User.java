package pl.kathelan.soap.user.domain;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class User {
    String id;
    String firstName;
    String lastName;
    String email;
    Address address;
}
