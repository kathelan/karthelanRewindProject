package pl.kathelan.soap.user.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;
import pl.kathelan.soap.user.domain.Address;
import pl.kathelan.soap.user.domain.User;
import pl.kathelan.soap.user.repository.UserRepository;

import java.util.Map;

import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

/**
 * UserEndpoint — integration tests for the SOAP user operations endpoint.
 *
 * <p>Tests verify the full Spring-WS stack (endpoint, mapper, repository) using
 * {@link MockWebServiceClient}. WS-Security UsernameToken header is included in every
 * request to satisfy the authentication interceptor configured in {@code WsSecurityConfig}.
 *
 * <p>Profile {@code local} activates in-memory repositories, so no external dependencies are needed.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("UserEndpoint — SOAP user operations integration")
class UserEndpointIntegrationTest {

    private static final String NS = "http://kathelan.pl/soap/users";
    private static final Map<String, String> NS_MAP = Map.of("tns", NS);

    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String PW_TEXT_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(context);
    }

    // ===== createUser =====

    @Nested
    @DisplayName("createUser — creating a new user via SOAP")
    class CreateUser {

        /**
         * Happy path: a valid createUserRequest with all required fields must return
         * a response containing a non-empty {@code id}, the submitted email, and the address city.
         * No {@code errorCode} element should be present in the response.
         */
        @Test
        @DisplayName("returns created user with id and no errorCode for valid request")
        void shouldCreateUser() throws Exception {
            client.sendRequest(withSoapEnvelope(envelope("""
                    <tns:createUserRequest xmlns:tns="%s">
                        <tns:firstName>Jan</tns:firstName>
                        <tns:lastName>Kowalski</tns:lastName>
                        <tns:email>jan@example.com</tns:email>
                        <tns:address>
                            <tns:street>ul. Testowa 1</tns:street>
                            <tns:city>Warsaw</tns:city>
                            <tns:zipCode>00-001</tns:zipCode>
                            <tns:country>Poland</tns:country>
                        </tns:address>
                    </tns:createUserRequest>
                    """.formatted(NS))))
                    .andExpect(noFault())
                    .andExpect(xpath("//tns:createUserResponse/tns:user/tns:id", NS_MAP).exists())
                    .andExpect(xpath("//tns:createUserResponse/tns:user/tns:email", NS_MAP).evaluatesTo("jan@example.com"))
                    .andExpect(xpath("//tns:createUserResponse/tns:user/tns:address/tns:city", NS_MAP).evaluatesTo("Warsaw"))
                    .andExpect(xpath("//tns:createUserResponse/tns:errorCode", NS_MAP).doesNotExist());
        }

        /**
         * Duplicate email scenario: when a user with the same email already exists,
         * the endpoint must respond with {@code USER_ALREADY_EXISTS} error code and no user element,
         * instead of overwriting or silently accepting the duplicate.
         */
        @Test
        @DisplayName("returns USER_ALREADY_EXISTS and no user when email is already taken")
        void shouldReturnErrorOnDuplicateEmail() throws Exception {
            saveUser("dup@example.com", "Warsaw");

            client.sendRequest(withSoapEnvelope(envelope("""
                    <tns:createUserRequest xmlns:tns="%s">
                        <tns:firstName>Other</tns:firstName>
                        <tns:lastName>Person</tns:lastName>
                        <tns:email>dup@example.com</tns:email>
                        <tns:address>
                            <tns:street>ul. Inna 2</tns:street>
                            <tns:city>Krakow</tns:city>
                            <tns:zipCode>30-001</tns:zipCode>
                            <tns:country>Poland</tns:country>
                        </tns:address>
                    </tns:createUserRequest>
                    """.formatted(NS))))
                    .andExpect(noFault())
                    .andExpect(xpath("//tns:createUserResponse/tns:errorCode", NS_MAP).evaluatesTo("USER_ALREADY_EXISTS"))
                    .andExpect(xpath("//tns:createUserResponse/tns:user", NS_MAP).doesNotExist());
        }
    }

    // ===== getUser =====

    @Nested
    @DisplayName("getUser — fetching a single user by ID")
    class GetUser {

        /**
         * Happy path: when a user exists in the repository, a getUser request with that
         * user's ID must return the correct email and no errorCode.
         */
        @Test
        @DisplayName("returns user data for an existing user id")
        void shouldGetUserById() throws Exception {
            String id = saveUser("anna@example.com", "Warsaw");

            client.sendRequest(withSoapEnvelope(envelope("""
                    <tns:getUserRequest xmlns:tns="%s">
                        <tns:id>%s</tns:id>
                    </tns:getUserRequest>
                    """.formatted(NS, id))))
                    .andExpect(noFault())
                    .andExpect(xpath("//tns:getUserResponse/tns:user/tns:email", NS_MAP).evaluatesTo("anna@example.com"))
                    .andExpect(xpath("//tns:getUserResponse/tns:errorCode", NS_MAP).doesNotExist());
        }

        /**
         * Not-found scenario: requesting a user by an ID that does not exist in the store
         * must return {@code USER_NOT_FOUND} error code and no user element,
         * rather than throwing an uncaught exception or returning a SOAP fault.
         */
        @Test
        @DisplayName("returns USER_NOT_FOUND and no user for an unknown id")
        void shouldReturnErrorWhenUserNotFound() throws Exception {
            client.sendRequest(withSoapEnvelope(envelope("""
                    <tns:getUserRequest xmlns:tns="%s">
                        <tns:id>nonexistent-id</tns:id>
                    </tns:getUserRequest>
                    """.formatted(NS))))
                    .andExpect(noFault())
                    .andExpect(xpath("//tns:getUserResponse/tns:errorCode", NS_MAP).evaluatesTo("USER_NOT_FOUND"))
                    .andExpect(xpath("//tns:getUserResponse/tns:user", NS_MAP).doesNotExist());
        }
    }

    // ===== getUsersByCity =====

    @Nested
    @DisplayName("getUsersByCity — searching users by city")
    class GetUsersByCity {

        /**
         * City filter: when multiple users exist in different cities, only users
         * whose address matches the requested city should be returned.
         * Users from other cities must be excluded from the result set.
         */
        @Test
        @DisplayName("returns only users from the requested city, excluding other cities")
        void shouldGetUsersByCity() throws Exception {
            saveUser("city1@example.com", "Gdansk");
            saveUser("city2@example.com", "Gdansk");
            saveUser("city3@example.com", "Poznan");

            client.sendRequest(withSoapEnvelope(envelope("""
                    <tns:getUsersByCityRequest xmlns:tns="%s">
                        <tns:city>Gdansk</tns:city>
                    </tns:getUsersByCityRequest>
                    """.formatted(NS))))
                    .andExpect(noFault())
                    .andExpect(xpath("count(//tns:getUsersByCityResponse/tns:users)", NS_MAP).evaluatesTo(2))
                    .andExpect(xpath("//tns:getUsersByCityResponse/tns:errorCode", NS_MAP).doesNotExist());
        }
    }

    // ===== helpers =====

    private StringSource envelope(String payload) {
        return new StringSource("""
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:wsse="%s">
                    <soapenv:Header>
                        <wsse:Security>
                            <wsse:UsernameToken>
                                <wsse:Username>admin</wsse:Username>
                                <wsse:Password Type="%s">adminpass</wsse:Password>
                            </wsse:UsernameToken>
                        </wsse:Security>
                    </soapenv:Header>
                    <soapenv:Body>%s</soapenv:Body>
                </soapenv:Envelope>
                """.formatted(WSSE_NS, PW_TEXT_TYPE, payload));
    }

    private String saveUser(String email, String city) {
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .address(Address.builder()
                        .street("ul. Testowa 1")
                        .city(city)
                        .zipCode("00-001")
                        .country("Poland")
                        .build())
                .build()
        ).getId();
    }
}
