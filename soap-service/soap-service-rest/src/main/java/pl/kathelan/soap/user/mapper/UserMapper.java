package pl.kathelan.soap.user.mapper;

import org.springframework.stereotype.Component;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.UserDto;
import pl.kathelan.soap.user.domain.Address;
import pl.kathelan.soap.user.domain.User;

@Component
public class UserMapper {

    public UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setAddress(toJaxbAddress(user.getAddress()));
        return dto;
    }

    public User toDomain(CreateUserRequest request) {
        return User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .address(toDomainAddress(request.getAddress()))
                .build();
    }

    public pl.kathelan.soap.api.generated.Address toJaxbAddress(Address address) {
        pl.kathelan.soap.api.generated.Address jaxb = new pl.kathelan.soap.api.generated.Address();
        jaxb.setStreet(address.getStreet());
        jaxb.setCity(address.getCity());
        jaxb.setZipCode(address.getZipCode());
        jaxb.setCountry(address.getCountry());
        return jaxb;
    }

    public Address toDomainAddress(pl.kathelan.soap.api.generated.Address jaxb) {
        return Address.builder()
                .street(jaxb.getStreet())
                .city(jaxb.getCity())
                .zipCode(jaxb.getZipCode())
                .country(jaxb.getCountry())
                .build();
    }
}
