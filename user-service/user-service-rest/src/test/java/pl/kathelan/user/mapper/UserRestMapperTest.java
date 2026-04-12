package pl.kathelan.user.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.kathelan.soap.api.generated.Address;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.UserDto;
import pl.kathelan.user.api.dto.AddressDto;
import pl.kathelan.user.api.dto.CreateUserRequestDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Living documentation for UserRestMapper.
 *
 * <p>Verifies that the mapper correctly translates between:
 * <ul>
 *   <li>JAXB-generated SOAP types (soap-service-api) → domain API records</li>
 *   <li>domain API records → JAXB-generated SOAP request types</li>
 * </ul>
 *
 * <p>Each field is verified explicitly to catch silent regressions
 * when the XSD or API DTOs change.
 */
@DisplayName("UserRestMapper — SOAP ↔ REST DTO mapping")
class UserRestMapperTest {

    private final UserRestMapper mapper = new UserRestMapper();

    @Nested
    @DisplayName("toDto(UserDto jaxb)")
    class ToDto {

        /**
         * All fields from the JAXB UserDto — including nested Address — must
         * be mapped to the corresponding fields of the API UserDto record.
         */
        @Test
        @DisplayName("maps all user fields including nested address")
        void shouldMapAllFields() {
            Address jaxbAddress = new Address();
            jaxbAddress.setStreet("ul. Testowa 1");
            jaxbAddress.setCity("Warsaw");
            jaxbAddress.setZipCode("00-001");
            jaxbAddress.setCountry("Poland");

            UserDto jaxbUser = new UserDto();
            jaxbUser.setId("user-1");
            jaxbUser.setFirstName("Jan");
            jaxbUser.setLastName("Kowalski");
            jaxbUser.setEmail("jan@example.com");
            jaxbUser.setAddress(jaxbAddress);

            pl.kathelan.user.api.dto.UserDto result = mapper.toDto(jaxbUser);

            assertThat(result.id()).isEqualTo("user-1");
            assertThat(result.firstName()).isEqualTo("Jan");
            assertThat(result.lastName()).isEqualTo("Kowalski");
            assertThat(result.email()).isEqualTo("jan@example.com");
            assertThat(result.address().street()).isEqualTo("ul. Testowa 1");
            assertThat(result.address().city()).isEqualTo("Warsaw");
            assertThat(result.address().zipCode()).isEqualTo("00-001");
            assertThat(result.address().country()).isEqualTo("Poland");
        }
    }

    @Nested
    @DisplayName("toSoapRequest(CreateUserRequestDto dto)")
    class ToSoapRequest {

        /**
         * All fields from the API CreateUserRequestDto — including nested AddressDto —
         * must be mapped to the corresponding JAXB CreateUserRequest fields.
         */
        @Test
        @DisplayName("maps all request fields including nested address")
        void shouldMapAllFields() {
            AddressDto addressDto = new AddressDto("ul. Kwiatowa 5", "Krakow", "30-001", "Poland");
            CreateUserRequestDto dto = new CreateUserRequestDto("Anna", "Nowak", "anna@example.com", addressDto);

            CreateUserRequest result = mapper.toSoapRequest(dto);

            assertThat(result.getFirstName()).isEqualTo("Anna");
            assertThat(result.getLastName()).isEqualTo("Nowak");
            assertThat(result.getEmail()).isEqualTo("anna@example.com");
            assertThat(result.getAddress().getStreet()).isEqualTo("ul. Kwiatowa 5");
            assertThat(result.getAddress().getCity()).isEqualTo("Krakow");
            assertThat(result.getAddress().getZipCode()).isEqualTo("30-001");
            assertThat(result.getAddress().getCountry()).isEqualTo("Poland");
        }
    }
}
