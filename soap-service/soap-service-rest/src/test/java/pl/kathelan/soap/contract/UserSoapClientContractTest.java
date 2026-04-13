package pl.kathelan.soap.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.client.WebServiceFaultException;
import pl.kathelan.soap.api.generated.Address;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.CreateUserResponse;
import pl.kathelan.soap.api.generated.ErrorCode;
import pl.kathelan.soap.api.generated.GetUserResponse;
import pl.kathelan.soap.api.generated.GetUsersByCityResponse;
import pl.kathelan.soap.client.UserSoapClient;
import pl.kathelan.soap.client.UserSoapClientImpl;
import pl.kathelan.soap.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UserSoapClientContractTest — producer-side contract tests between soap-service-client and soap-service-rest.
 *
 * <p>These tests make real HTTP calls from {@link UserSoapClientImpl} to the running
 * {@code soap-service-rest} application (started on a random port). No mocks are used.
 * The full WS-Security stack (WSS4J UsernameToken) is active on both sides.
 *
 * <p>Purpose: verify that the client and server agree on the SOAP contract for all user operations.
 * Any breaking schema or logic change on the server side will be caught here before deployment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DisplayName("UserSoapClientContractTest — producer-side contract between client and server")
class UserSoapClientContractTest extends SoapContractTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private UserSoapClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = new UserSoapClientImpl(buildSecuredTemplate(port));
    }

    // ===== createUser + getUser round-trip =====

    @Nested
    @DisplayName("createUser and getUser — full round-trip over real HTTP")
    class CreateAndGetUser {

        /**
         * Create-then-get: creating a user via the client must succeed (no errorCode),
         * and fetching the returned id must yield a user with the same email and city.
         * This is the core contract: what is created must be retrievable.
         */
        @Test
        @DisplayName("creates a user and retrieves it by the returned id with matching fields")
        void shouldCreateUserAndGetItBack() {
            CreateUserResponse created = client.createUser(buildCreateRequest("contract@example.com", "Warsaw"));

            assertThat(created.getErrorCode()).isNull();
            assertThat(created.getUser()).isNotNull();
            String id = created.getUser().getId();

            GetUserResponse fetched = client.getUser(id);

            assertThat(fetched.getUser()).isNotNull();
            assertThat(fetched.getUser().getId()).isEqualTo(id);
            assertThat(fetched.getUser().getEmail()).isEqualTo("contract@example.com");
            assertThat(fetched.getUser().getAddress().getCity()).isEqualTo("Warsaw");
        }
    }

    // ===== getUser error contract =====

    @Nested
    @DisplayName("getUser — error contract for non-existent users")
    class GetUserErrors {

        /**
         * Not found: requesting a user by an id that does not exist must return
         * {@code USER_NOT_FOUND} error code and a null user in the response.
         * The client must not throw an exception for domain-level errors.
         */
        @Test
        @DisplayName("returns USER_NOT_FOUND error code and null user for an unknown id")
        void shouldReturnUserNotFoundError() {
            GetUserResponse response = client.getUser("nonexistent-id");

            assertThat(response.getUser()).isNull();
            assertThat(response.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    // ===== createUser error contract =====

    @Nested
    @DisplayName("createUser — error contract for duplicate emails")
    class CreateUserErrors {

        /**
         * Duplicate email: creating a second user with the same email must return
         * {@code USER_ALREADY_EXISTS} error code and a null user.
         * The first create must not be rolled back; only the second must fail.
         */
        @Test
        @DisplayName("returns USER_ALREADY_EXISTS and null user when email is already taken")
        void shouldReturnDuplicateEmailError() {
            client.createUser(buildCreateRequest("dup-contract@example.com", "Krakow"));

            CreateUserResponse second = client.createUser(buildCreateRequest("dup-contract@example.com", "Gdansk"));

            assertThat(second.getUser()).isNull();
            assertThat(second.getErrorCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
        }
    }

    // ===== getUsersByCity contract =====

    @Nested
    @DisplayName("getUsersByCity — contract for city-based user listing")
    class GetUsersByCity {

        /**
         * City filter: after creating multiple users in the same city (and one in another),
         * getUsersByCity must return at least the two users in the queried city,
         * all with the matching city in their address.
         */
        @Test
        @DisplayName("returns at least the two created users for a city, all with the correct city")
        void shouldGetUsersByCity() {
            client.createUser(buildCreateRequest("city-c1@example.com", "Poznan"));
            client.createUser(buildCreateRequest("city-c2@example.com", "Poznan"));
            client.createUser(buildCreateRequest("city-c3@example.com", "Lublin"));

            GetUsersByCityResponse response = client.getUsersByCity("Poznan");

            assertThat(response.getUsers())
                    .hasSizeGreaterThanOrEqualTo(2)
                    .allMatch(u -> u.getAddress().getCity().equals("Poznan"));
        }

        /**
         * Empty city: querying a city where no users exist must return an empty list.
         * The client must not throw an exception and the response list must be empty (not null).
         * This verifies the contract for the zero-result case.
         */
        @Test
        @DisplayName("returns empty list when no users exist in the queried city")
        void shouldReturnEmptyList_whenCityHasNoUsers() {
            GetUsersByCityResponse response = client.getUsersByCity("EmptyContractCity");

            assertThat(response.getUsers()).isEmpty();
        }
    }

    // ===== security contract =====

    @Nested
    @DisplayName("security contract — WS-Security enforcement from the client side")
    class SecurityContract {

        /**
         * Missing WS-Security header: a client configured without a security interceptor
         * must receive a {@link WebServiceFaultException} when it sends any request.
         * This confirms the server actively enforces WS-Security on every call.
         */
        @Test
        @DisplayName("throws WebServiceFaultException when the WS-Security header is absent")
        void shouldRejectRequestWithoutWsSecurityHeader() throws Exception {
            UserSoapClient unauthClient = new UserSoapClientImpl(buildUnsecuredTemplate(port));

            assertThatThrownBy(() -> unauthClient.getUser("any-id"))
                    .isInstanceOf(WebServiceFaultException.class);
        }
    }

    // ===== helpers =====

    private CreateUserRequest buildCreateRequest(String email, String city) {
        Address address = new Address();
        address.setStreet("ul. Kontraktowa 1");
        address.setCity(city);
        address.setZipCode("00-001");
        address.setCountry("Poland");

        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Contract");
        request.setLastName("Test");
        request.setEmail(email);
        request.setAddress(address);
        return request;
    }
}
