package pl.kathelan.soap.wsdl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WsdlGenerationTest — verifies that the service exposes a valid WSDL matching the users.xsd contract.
 *
 * <p>The WSDL endpoint ({@code /ws/users.wsdl}) is public — no WS-Security credentials are
 * required to access it. Tests confirm both availability and content correctness so that
 * client-side code generation (from the WSDL) remains aligned with the server contract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DisplayName("WsdlGenerationTest — WSDL exposure and contract content")
class WsdlGenerationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // ===== availability =====

    @Nested
    @DisplayName("availability — WSDL is publicly reachable without authentication")
    class Availability {

        /**
         * Public access: the WSDL must be served with HTTP 200 without any authentication header.
         * This is intentional — WS clients must be able to fetch the contract before they
         * can configure their WS-Security tokens.
         */
        @Test
        @DisplayName("responds with HTTP 200 for an unauthenticated GET to /ws/users.wsdl")
        void shouldExposeWsdlWithoutAuthentication() {
            ResponseEntity<String> response = restTemplate.getForEntity("/ws/users.wsdl", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ===== content =====

    @Nested
    @DisplayName("content — WSDL structure matches the users.xsd contract")
    class Content {

        /**
         * WSDL definitions root: the document must contain a {@code wsdl:definitions} element
         * with the correct targetNamespace, confirming it is a valid WSDL and not an error page.
         */
        @Test
        @DisplayName("contains wsdl:definitions with the correct targetNamespace")
        void shouldReturnValidWsdlDefinitions() {
            String wsdl = fetchWsdl();

            assertThat(wsdl).contains("wsdl:definitions");
            assertThat(wsdl).contains("targetNamespace=\"http://kathelan.pl/soap/users\"");
        }

        /**
         * All three operations: the WSDL must advertise all three user operations
         * (getUser, createUser, getUsersByCity) so clients can generate complete stubs.
         */
        @Test
        @DisplayName("exposes all three user operations: getUser, createUser, getUsersByCity")
        void shouldExposeAllThreeOperations() {
            String wsdl = fetchWsdl();

            assertThat(wsdl).contains("getUserRequest");
            assertThat(wsdl).contains("createUserRequest");
            assertThat(wsdl).contains("getUsersByCityRequest");
        }

        /**
         * UsersPort: the WSDL must declare the {@code UsersPort} port type so that
         * generated client classes bind to the correct service interface.
         */
        @Test
        @DisplayName("declares the UsersPort port type")
        void shouldExposeUsersPortType() {
            String wsdl = fetchWsdl();

            assertThat(wsdl).contains("UsersPort");
        }

        /**
         * WS endpoint address: the WSDL must include the {@code /ws} path in the service
         * endpoint address so clients know where to send requests without manual configuration.
         */
        @Test
        @DisplayName("includes /ws endpoint address in the service binding")
        void shouldExposeWsEndpointAddress() {
            String wsdl = fetchWsdl();

            assertThat(wsdl).contains("/ws");
        }
    }

    // ===== helper =====

    private String fetchWsdl() {
        ResponseEntity<String> response = restTemplate.getForEntity("/ws/users.wsdl", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
