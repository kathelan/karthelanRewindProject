package pl.kathelan.user.contract;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CountBasedCircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;
import pl.kathelan.soap.client.UserSoapClientImpl;
import pl.kathelan.user.api.dto.AddressDto;
import pl.kathelan.user.api.dto.CreateUserRequestDto;
import pl.kathelan.user.api.dto.UserDto;
import pl.kathelan.user.exception.UserAlreadyExistsException;
import pl.kathelan.user.exception.UserNotFoundException;
import pl.kathelan.user.mapper.UserRestMapper;
import pl.kathelan.user.service.UserServiceImpl;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consumer-side SOAP contract tests for UserService → soap-service integration.
 *
 * <p>WireMock acts as a stub of the SOAP producer (soap-service).
 * Contract stubs are defined in src/test/resources/wiremock/mappings/ —
 * they are versioned artifacts describing what this consumer expects.
 *
 * <p>What this test verifies:
 * <ol>
 *   <li>UserSoapClientImpl serializes SOAP requests with the correct XML structure</li>
 *   <li>UserSoapClientImpl correctly deserializes SOAP responses</li>
 *   <li>UserServiceImpl maps SOAP responses to domain DTOs and throws the right exceptions</li>
 * </ol>
 *
 * <p>No real soap-service instance required — WireMock matches requests by XPath on the SOAP body.
 */
@DisplayName("UserService — SOAP Contract Tests (consumer side)")
class UserServiceSoapContractTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock"))
            .build();

    private UserServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("pl.kathelan.soap.api.generated");
        marshaller.afterPropertiesSet();

        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        template.setDefaultUri(wm.baseUrl() + "/ws");

        UserSoapClientImpl soapClient = new UserSoapClientImpl(template);

        CircuitBreakerConfig cbConfig = new CircuitBreakerConfig(
                5, Duration.ofSeconds(10),
                e -> !(e instanceof UserNotFoundException || e instanceof UserAlreadyExistsException)
        );
        CountBasedCircuitBreaker cb = new CountBasedCircuitBreaker(
                "soap-contract-test", cbConfig, new InMemoryCircuitBreakerStateRepository()
        );
        RetryConfig retryConfig = new RetryConfig(1, Duration.ZERO, 1.0, Set.of(RuntimeException.class));
        ResilientCaller resilientCaller = new ResilientCaller(cb, new RetryExecutor(), retryConfig);

        service = new UserServiceImpl(soapClient, new UserRestMapper(), resilientCaller);
    }

    @Nested
    @DisplayName("getUser")
    class GetUser {

        /**
         * Producer contract: getUserResponse with full UserDto when user exists.
         * Consumer expectation: maps to UserDto record with all fields populated.
         */
        @Test
        @DisplayName("returns UserDto when soap-service returns user")
        void shouldReturnUserDto_whenProducerReturnsUser() {
            UserDto result = service.getUser("user-contract-1");

            assertThat(result.id()).isEqualTo("user-contract-1");
            assertThat(result.firstName()).isEqualTo("Jan");
            assertThat(result.lastName()).isEqualTo("Kowalski");
            assertThat(result.email()).isEqualTo("jan.kowalski@contract.pl");
            assertThat(result.address().city()).isEqualTo("Warsaw");
        }

        /**
         * Producer contract: getUserResponse with errorCode=USER_NOT_FOUND when user missing.
         * Consumer expectation: throws UserNotFoundException with the requested id in the message.
         */
        @Test
        @DisplayName("throws UserNotFoundException when soap-service returns USER_NOT_FOUND")
        void shouldThrowUserNotFoundException_whenProducerReturnsUserNotFound() {
            assertThatThrownBy(() -> service.getUser("nonexistent-id"))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("nonexistent-id");
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        /**
         * Producer contract: createUserResponse with full UserDto when email is unique.
         * Consumer expectation: maps to UserDto with generated id.
         */
        @Test
        @DisplayName("returns UserDto with generated id when soap-service creates user")
        void shouldReturnCreatedUserDto_whenProducerCreatesUser() {
            CreateUserRequestDto dto = new CreateUserRequestDto(
                    "Anna", "Nowak", "new.user@contract.pl",
                    new AddressDto("ul. Nowa 1", "Krakow", "30-001", "Poland")
            );

            UserDto result = service.createUser(dto);

            assertThat(result.id()).isNotEmpty();
            assertThat(result.email()).isEqualTo("new.user@contract.pl");
            assertThat(result.address().city()).isEqualTo("Krakow");
        }

        /**
         * Producer contract: createUserResponse with errorCode=USER_ALREADY_EXISTS on duplicate email.
         * Consumer expectation: throws UserAlreadyExistsException.
         */
        @Test
        @DisplayName("throws UserAlreadyExistsException when soap-service returns USER_ALREADY_EXISTS")
        void shouldThrowUserAlreadyExistsException_whenProducerReturnsDuplicate() {
            CreateUserRequestDto dto = new CreateUserRequestDto(
                    "Duplikat", "Kowalski", "existing@contract.pl",
                    new AddressDto("ul. Stara 1", "Warsaw", "00-001", "Poland")
            );

            assertThatThrownBy(() -> service.createUser(dto))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }
    }

    @Nested
    @DisplayName("getUsersByCity")
    class GetUsersByCity {

        /**
         * Producer contract: getUsersByCityResponse with list of UserDto for a given city.
         * Consumer expectation: returns list of domain UserDtos, one per SOAP user element.
         */
        @Test
        @DisplayName("returns all UserDtos from the city when soap-service returns users")
        void shouldReturnUserList_whenProducerReturnsUsersForCity() {
            List<UserDto> result = service.getUsersByCity("Warsaw");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserDto::email)
                    .containsExactlyInAnyOrder("jan@contract.pl", "maria@contract.pl");
            assertThat(result).extracting(u -> u.address().city())
                    .containsOnly("Warsaw");
        }

        /**
         * Producer contract: getUsersByCityResponse with empty body when no users in city.
         * Consumer expectation: returns empty list — absence is a valid result, not an error.
         * Guards against producers changing from empty element to omitting it entirely.
         */
        @Test
        @DisplayName("returns empty list when soap-service returns no users for city")
        void shouldReturnEmptyList_whenProducerReturnsNoUsers() {
            List<UserDto> result = service.getUsersByCity("EmptyCity");

            assertThat(result).isEmpty();
        }
    }
}
