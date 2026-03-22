package pl.kathelan.soap.wsdl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the service exposes a valid WSDL matching the users.xsd contract.
 * WSDL endpoint is public (no auth required).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class WsdlGenerationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldExposeWsdlWithoutAuthentication() {
        ResponseEntity<String> response = restTemplate.getForEntity("/ws/users.wsdl", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldReturnValidWsdlDefinitions() {
        String wsdl = fetchWsdl();

        assertThat(wsdl).contains("wsdl:definitions");
        assertThat(wsdl).contains("targetNamespace=\"http://kathelan.pl/soap/users\"");
    }

    @Test
    void shouldExposeAllThreeOperations() {
        String wsdl = fetchWsdl();

        assertThat(wsdl).contains("getUserRequest");
        assertThat(wsdl).contains("createUserRequest");
        assertThat(wsdl).contains("getUsersByCityRequest");
    }

    @Test
    void shouldExposeUsersPortType() {
        String wsdl = fetchWsdl();

        assertThat(wsdl).contains("UsersPort");
    }

    @Test
    void shouldExposeWsEndpointAddress() {
        String wsdl = fetchWsdl();

        assertThat(wsdl).contains("/ws");
    }

    // ===== helper =====

    private String fetchWsdl() {
        ResponseEntity<String> response = restTemplate.getForEntity("/ws/users.wsdl", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
