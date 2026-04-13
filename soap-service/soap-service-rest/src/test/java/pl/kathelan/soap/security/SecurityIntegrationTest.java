package pl.kathelan.soap.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityIntegrationTest — integration tests for WS-Security UsernameToken enforcement.
 *
 * <p>Auth is at the SOAP message level (WSS4J UsernameToken), not HTTP Basic Auth.
 * Missing or invalid credentials result in a SOAP Fault returned with HTTP 500.
 * Valid credentials yield HTTP 200 with a valid SOAP response body (no Fault element).
 *
 * <p>Tests send raw HTTP POST requests to the {@code /ws} endpoint so the full
 * Spring-WS security interceptor chain is exercised, including {@code WsSecurityConfig}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DisplayName("SecurityIntegrationTest — WS-Security UsernameToken enforcement")
class SecurityIntegrationTest {

    private static final String SOAP_PATH = "/ws";

    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String PW_TEXT_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

    @Autowired
    private TestRestTemplate restTemplate;

    // ===== authentication failures =====

    @Nested
    @DisplayName("authentication failures — requests without or with wrong credentials")
    class AuthFailures {

        /**
         * No security header: a SOAP request without any WS-Security header must be
         * rejected with HTTP 500 and a SOAP Fault in the response body.
         * This verifies that unauthenticated access is completely blocked.
         */
        @Test
        @DisplayName("returns HTTP 500 with SOAP Fault when no WS-Security header is present")
        void shouldReturnSoapFaultWhenNoSecurityHeader() {
            ResponseEntity<String> response = postSoap(soapBodyOnly());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).contains("Fault");
        }

        /**
         * Wrong password: even if the username is correct, using the wrong password must
         * result in HTTP 500 with a SOAP Fault. The service must not partially authenticate.
         */
        @Test
        @DisplayName("returns HTTP 500 with SOAP Fault when correct username but wrong password is used")
        void shouldReturnSoapFaultWhenWrongPassword() {
            ResponseEntity<String> response = postSoap(soapWithCredentials("admin", "wrongpassword"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).contains("Fault");
        }
    }

    // ===== successful authentication =====

    @Nested
    @DisplayName("successful authentication — requests with valid credentials")
    class AuthSuccess {

        /**
         * Admin credentials: the {@code admin/adminpass} pair configured via
         * {@code soap.security.users.admin} must be accepted, yielding HTTP 200
         * and a response body free of SOAP Fault elements.
         */
        @Test
        @DisplayName("returns HTTP 200 without Fault element when admin credentials are valid")
        void shouldReturn200WhenValidAdminCredentials() {
            ResponseEntity<String> response = postSoap(soapWithCredentials("admin", "adminpass"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).doesNotContain("Fault");
        }

        /**
         * User credentials: the {@code user/userpass} pair must also be accepted,
         * confirming that multiple valid credential sets are supported by the callback handler.
         */
        @Test
        @DisplayName("returns HTTP 200 without Fault element when user credentials are valid")
        void shouldReturn200WhenValidUserCredentials() {
            ResponseEntity<String> response = postSoap(soapWithCredentials("user", "userpass"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).doesNotContain("Fault");
        }
    }

    // ===== helpers =====

    private String soapBodyOnly() {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:tns="http://kathelan.pl/soap/users">
                    <soapenv:Body>
                        <tns:getUserRequest>
                            <tns:id>test-id</tns:id>
                        </tns:getUserRequest>
                    </soapenv:Body>
                </soapenv:Envelope>
                """;
    }

    private String soapWithCredentials(String username, String password) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:tns="http://kathelan.pl/soap/users"
                                  xmlns:wsse="%s">
                    <soapenv:Header>
                        <wsse:Security>
                            <wsse:UsernameToken>
                                <wsse:Username>%s</wsse:Username>
                                <wsse:Password Type="%s">%s</wsse:Password>
                            </wsse:UsernameToken>
                        </wsse:Security>
                    </soapenv:Header>
                    <soapenv:Body>
                        <tns:getUserRequest>
                            <tns:id>test-id</tns:id>
                        </tns:getUserRequest>
                    </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(WSSE_NS, username, PW_TEXT_TYPE, password);
    }

    private ResponseEntity<String> postSoap(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/xml");
        headers.set("SOAPAction", "\"\"");
        return restTemplate.exchange(SOAP_PATH, HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }
}
