package pl.kathelan.soap.mapper;

import org.junit.jupiter.api.Test;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.UserDto;
import pl.kathelan.soap.domain.Address;
import pl.kathelan.soap.domain.User;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void shouldMapDomainUserToDto() {
        User user = User.builder()
                .id("id-1")
                .firstName("Jan")
                .lastName("Kowalski")
                .email("jan@example.com")
                .address(Address.builder()
                        .street("ul. Testowa 1")
                        .city("Warsaw")
                        .zipCode("00-001")
                        .country("Poland")
                        .build())
                .build();

        UserDto dto = mapper.toDto(user);

        assertThat(dto.getId()).isEqualTo("id-1");
        assertThat(dto.getFirstName()).isEqualTo("Jan");
        assertThat(dto.getLastName()).isEqualTo("Kowalski");
        assertThat(dto.getEmail()).isEqualTo("jan@example.com");
        assertThat(dto.getAddress().getCity()).isEqualTo("Warsaw");
        assertThat(dto.getAddress().getStreet()).isEqualTo("ul. Testowa 1");
        assertThat(dto.getAddress().getZipCode()).isEqualTo("00-001");
        assertThat(dto.getAddress().getCountry()).isEqualTo("Poland");
    }

    @Test
    void shouldMapCreateUserRequestToDomain() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Anna");
        request.setLastName("Nowak");
        request.setEmail("anna@example.com");

        pl.kathelan.soap.api.generated.Address jaxbAddress = new pl.kathelan.soap.api.generated.Address();
        jaxbAddress.setStreet("ul. Kwiatowa 5");
        jaxbAddress.setCity("Krakow");
        jaxbAddress.setZipCode("30-001");
        jaxbAddress.setCountry("Poland");
        request.setAddress(jaxbAddress);

        User user = mapper.toDomain(request);

        assertThat(user.getId()).isNull();
        assertThat(user.getFirstName()).isEqualTo("Anna");
        assertThat(user.getLastName()).isEqualTo("Nowak");
        assertThat(user.getEmail()).isEqualTo("anna@example.com");
        assertThat(user.getAddress().getCity()).isEqualTo("Krakow");
        assertThat(user.getAddress().getStreet()).isEqualTo("ul. Kwiatowa 5");
    }

    @Test
    void shouldMapAddressBidirectionally() {
        Address domain = Address.builder()
                .street("ul. Morska 3")
                .city("Gdansk")
                .zipCode("80-001")
                .country("Poland")
                .build();

        pl.kathelan.soap.api.generated.Address jaxb = mapper.toJaxbAddress(domain);
        Address result = mapper.toDomainAddress(jaxb);

        assertThat(result).isEqualTo(domain);
    }
}
