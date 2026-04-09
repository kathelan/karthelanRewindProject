package pl.kathelan.soap.user.endpoint;

import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@ActiveProfiles("local")
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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

    @Test
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
