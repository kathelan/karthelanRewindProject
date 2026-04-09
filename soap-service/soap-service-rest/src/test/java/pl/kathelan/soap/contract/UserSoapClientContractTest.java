package pl.kathelan.soap.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.client.WebServiceFaultException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
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
 * Contract test — real HTTP calls from soap-service-client to soap-service-rest.
 * No mocks. Full WS-Security stack included.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class UserSoapClientContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private UserSoapClient client;

    @BeforeEach
    void setUp() throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("pl.kathelan.soap.api.generated");
        marshaller.afterPropertiesSet();

        Wss4jSecurityInterceptor wsSecurityInterceptor = new Wss4jSecurityInterceptor();
        wsSecurityInterceptor.setSecurementActions("UsernameToken");
        wsSecurityInterceptor.setSecurementUsername("admin");
        wsSecurityInterceptor.setSecurementPassword("adminpass");
        wsSecurityInterceptor.setSecurementPasswordType("PasswordText");
        wsSecurityInterceptor.afterPropertiesSet();

        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        template.setDefaultUri("http://localhost:" + port + "/ws");
        template.setInterceptors(new org.springframework.ws.client.support.interceptor.ClientInterceptor[]{
                wsSecurityInterceptor
        });

        client = new UserSoapClientImpl(template);
    }

    @Test
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

    @Test
    void shouldReturnUserNotFoundError() {
        GetUserResponse response = client.getUser("nonexistent-id");

        assertThat(response.getUser()).isNull();
        assertThat(response.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void shouldReturnDuplicateEmailError() {
        client.createUser(buildCreateRequest("dup-contract@example.com", "Krakow"));

        CreateUserResponse second = client.createUser(buildCreateRequest("dup-contract@example.com", "Gdansk"));

        assertThat(second.getUser()).isNull();
        assertThat(second.getErrorCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    void shouldGetUsersByCity() {
        client.createUser(buildCreateRequest("city-c1@example.com", "Poznan"));
        client.createUser(buildCreateRequest("city-c2@example.com", "Poznan"));
        client.createUser(buildCreateRequest("city-c3@example.com", "Lublin"));

        GetUsersByCityResponse response = client.getUsersByCity("Poznan");

        assertThat(response.getUsers())
                .hasSizeGreaterThanOrEqualTo(2)
                .allMatch(u -> u.getAddress().getCity().equals("Poznan"));
    }

    @Test
    void shouldRejectRequestWithoutWsSecurityHeader() throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("pl.kathelan.soap.api.generated");
        marshaller.afterPropertiesSet();

        WebServiceTemplate unauthTemplate = new WebServiceTemplate();
        unauthTemplate.setMarshaller(marshaller);
        unauthTemplate.setUnmarshaller(marshaller);
        unauthTemplate.setDefaultUri("http://localhost:" + port + "/ws");

        UserSoapClient unauthClient = new UserSoapClientImpl(unauthTemplate);

        assertThatThrownBy(() -> unauthClient.getUser("any-id"))
                .isInstanceOf(WebServiceFaultException.class);
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
