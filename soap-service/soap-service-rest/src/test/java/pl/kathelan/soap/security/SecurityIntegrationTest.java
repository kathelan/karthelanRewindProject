package pl.kathelan.soap.security;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class SecurityIntegrationTest {

    private static final String SOAP_PATH = "/ws";
    private static final String VALID_SOAP_BODY = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:tns="http://kathelan.pl/soap/users">
                <soapenv:Body>
                    <tns:getUserRequest>
                        <tns:id>test-id</tns:id>
                    </tns:getUserRequest>
                </soapenv:Body>
            </soapenv:Envelope>
            """;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturn401WhenNoCredentials() {
        ResponseEntity<String> response = postSoap(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401WhenWrongPassword() {
        ResponseEntity<String> response = postSoap("admin", "wrongpassword");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn200WhenValidAdminCredentials() {
        ResponseEntity<String> response = postSoap("admin", "adminpass");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReturn200WhenValidUserCredentials() {
        ResponseEntity<String> response = postSoap("user", "userpass");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ===== helpers =====

    private ResponseEntity<String> postSoap(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/xml");
        headers.set("SOAPAction", "\"\"");

        TestRestTemplate client = (username != null)
                ? restTemplate.withBasicAuth(username, password)
                : restTemplate;

        return client.exchange(SOAP_PATH, HttpMethod.POST, new HttpEntity<>(VALID_SOAP_BODY, headers), String.class);
    }
}
