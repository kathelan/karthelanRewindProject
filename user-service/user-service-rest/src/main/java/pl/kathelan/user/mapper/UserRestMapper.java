package pl.kathelan.user.mapper;

import org.springframework.stereotype.Component;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.UserDto;
import pl.kathelan.user.api.dto.AddressDto;
import pl.kathelan.user.api.dto.CreateUserRequestDto;

@Component
public class UserRestMapper {

    public pl.kathelan.user.api.dto.UserDto toDto(UserDto jaxb) {
        return new pl.kathelan.user.api.dto.UserDto(
                jaxb.getId(),
                jaxb.getFirstName(),
                jaxb.getLastName(),
                jaxb.getEmail(),
                toAddressDto(jaxb.getAddress())
        );
    }

    public CreateUserRequest toSoapRequest(CreateUserRequestDto dto) {
        pl.kathelan.soap.api.generated.Address address = new pl.kathelan.soap.api.generated.Address();
        address.setStreet(dto.address().street());
        address.setCity(dto.address().city());
        address.setZipCode(dto.address().zipCode());
        address.setCountry(dto.address().country());

        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName(dto.firstName());
        request.setLastName(dto.lastName());
        request.setEmail(dto.email());
        request.setAddress(address);
        return request;
    }

    private AddressDto toAddressDto(pl.kathelan.soap.api.generated.Address jaxb) {
        return new AddressDto(
                jaxb.getStreet(),
                jaxb.getCity(),
                jaxb.getZipCode(),
                jaxb.getCountry()
        );
    }
}
