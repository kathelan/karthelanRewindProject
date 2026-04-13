package pl.kathelan.soap.user.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.UserDto;
import pl.kathelan.soap.user.domain.Address;
import pl.kathelan.soap.user.domain.User;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserMapper — unit tests for bidirectional mapping between domain objects and JAXB DTOs.
 *
 * <p>Verifies that {@link UserMapper} correctly maps:
 * <ul>
 *   <li>domain {@link User} → JAXB {@link UserDto} ({@code toDto})</li>
 *   <li>JAXB {@link CreateUserRequest} → domain {@link User} ({@code toDomain})</li>
 *   <li>domain {@link Address} ↔ JAXB Address ({@code toJaxbAddress} / {@code toDomainAddress})</li>
 * </ul>
 */
@DisplayName("UserMapper — domain-to-DTO and DTO-to-domain mapping")
class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    // ===== toDto =====

    @Nested
    @DisplayName("toDto — mapping domain User to JAXB UserDto")
    class ToDto {

        /**
         * Full mapping: every field of the domain {@link User} (id, firstName, lastName,
         * email, and all address sub-fields) must appear unchanged in the resulting {@link UserDto}.
         * This is the path used when serialising the response to the SOAP client.
         */
        @Test
        @DisplayName("maps all User fields including nested address to UserDto without loss")
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
    }

    // ===== toDomain =====

    @Nested
    @DisplayName("toDomain — mapping JAXB CreateUserRequest to domain User")
    class ToDomain {

        /**
         * Incoming request mapping: all fields from the JAXB request (firstName, lastName,
         * email, address) must be transferred to the domain object. The domain id must remain
         * null because it is not known before persistence.
         */
        @Test
        @DisplayName("maps all CreateUserRequest fields to domain User with null id")
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
    }

    // ===== address round-trip =====

    @Nested
    @DisplayName("address round-trip — toJaxbAddress and toDomainAddress are inverse operations")
    class AddressRoundTrip {

        /**
         * Bidirectional address mapping: converting a domain {@link Address} to its JAXB
         * representation and back must produce an object equal to the original.
         * This guards against accidentally dropping or mutating fields in either direction.
         */
        @Test
        @DisplayName("toJaxbAddress followed by toDomainAddress returns an equal domain Address")
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
}
