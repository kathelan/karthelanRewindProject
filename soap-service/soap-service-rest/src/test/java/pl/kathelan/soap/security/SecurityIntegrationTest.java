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

/**
 * WS-Security integration tests.
 * Auth jest na poziomie komunikatu SOAP (UsernameToken), nie HTTP.
 * Brak / złe credentials → SOAP Fault (HTTP 500).
 * Poprawne credentials → HTTP 200 z poprawną odpowiedzią.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class SecurityIntegrationTest {

    private static final String SOAP_PATH = "/ws";

    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String PW_TEXT_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturnSoapFaultWhenNoSecurityHeader() {
        ResponseEntity<String> response = postSoap(soapBodyOnly());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Fault");
    }

    @Test
    void shouldReturnSoapFaultWhenWrongPassword() {
        ResponseEntity<String> response = postSoap(soapWithCredentials("admin", "wrongpassword"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).contains("Fault");
    }

    @Test
    void shouldReturn200WhenValidAdminCredentials() {
        ResponseEntity<String> response = postSoap(soapWithCredentials("admin", "adminpass"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("Fault");
    }

    @Test
    void shouldReturn200WhenValidUserCredentials() {
        ResponseEntity<String> response = postSoap(soapWithCredentials("user", "userpass"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain("Fault");
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
