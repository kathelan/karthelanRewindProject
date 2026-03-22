package pl.kathelan.soap.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.CreateUserResponse;
import pl.kathelan.soap.api.generated.GetUserResponse;
import pl.kathelan.soap.api.generated.GetUsersByCityResponse;
import pl.kathelan.soap.client.config.SoapClientAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

@SpringBootTest(classes = SoapClientAutoConfiguration.class)
@TestPropertySource(properties = {
        "soap.client.url=http://localhost:8080/ws",
        "soap.client.username=admin",
        "soap.client.password=adminpass"
})
class UserSoapClientTest {

    private static final String NS = "http://kathelan.pl/soap/users";

    @Autowired
    private UserSoapClient client;

    @Autowired
    private WebServiceTemplate webServiceTemplate;

    private MockWebServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockWebServiceServer.createServer(webServiceTemplate);
    }

    @Test
    void shouldGetUserById() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:getUserRequest xmlns:tns="%s">
                            <tns:id>id-1</tns:id>
                        </tns:getUserRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:getUserResponse xmlns:tns="%s">
                            <tns:user>
                                <tns:id>id-1</tns:id>
                                <tns:firstName>Jan</tns:firstName>
                                <tns:lastName>Kowalski</tns:lastName>
                                <tns:email>jan@example.com</tns:email>
                                <tns:address>
                                    <tns:street>ul. Testowa 1</tns:street>
                                    <tns:city>Warsaw</tns:city>
                                    <tns:zipCode>00-001</tns:zipCode>
                                    <tns:country>Poland</tns:country>
                                </tns:address>
                            </tns:user>
                        </tns:getUserResponse>
                        """.formatted(NS))));

        GetUserResponse response = client.getUser("id-1");

        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getId()).isEqualTo("id-1");
        assertThat(response.getUser().getEmail()).isEqualTo("jan@example.com");
        assertThat(response.getErrorCode()).isNull();
        mockServer.verify();
    }

    @Test
    void shouldReturnErrorCodeWhenUserNotFound() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:getUserRequest xmlns:tns="%s">
                            <tns:id>nonexistent</tns:id>
                        </tns:getUserRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:getUserResponse xmlns:tns="%s">
                            <tns:errorCode>USER_NOT_FOUND</tns:errorCode>
                            <tns:message>User not found</tns:message>
                        </tns:getUserResponse>
                        """.formatted(NS))));

        GetUserResponse response = client.getUser("nonexistent");

        assertThat(response.getUser()).isNull();
        assertThat(response.getErrorCode()).isNotNull();
        assertThat(response.getErrorCode().value()).isEqualTo("USER_NOT_FOUND");
        mockServer.verify();
    }

    @Test
    void shouldCreateUser() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:createUserRequest xmlns:tns="%s">
                            <tns:firstName>Anna</tns:firstName>
                            <tns:lastName>Nowak</tns:lastName>
                            <tns:email>anna@example.com</tns:email>
                            <tns:address>
                                <tns:street>ul. Kwiatowa 5</tns:street>
                                <tns:city>Krakow</tns:city>
                                <tns:zipCode>30-001</tns:zipCode>
                                <tns:country>Poland</tns:country>
                            </tns:address>
                        </tns:createUserRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:createUserResponse xmlns:tns="%s">
                            <tns:user>
                                <tns:id>new-id</tns:id>
                                <tns:firstName>Anna</tns:firstName>
                                <tns:lastName>Nowak</tns:lastName>
                                <tns:email>anna@example.com</tns:email>
                                <tns:address>
                                    <tns:street>ul. Kwiatowa 5</tns:street>
                                    <tns:city>Krakow</tns:city>
                                    <tns:zipCode>30-001</tns:zipCode>
                                    <tns:country>Poland</tns:country>
                                </tns:address>
                            </tns:user>
                        </tns:createUserResponse>
                        """.formatted(NS))));

        pl.kathelan.soap.api.generated.Address address = new pl.kathelan.soap.api.generated.Address();
        address.setStreet("ul. Kwiatowa 5");
        address.setCity("Krakow");
        address.setZipCode("30-001");
        address.setCountry("Poland");

        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Anna");
        request.setLastName("Nowak");
        request.setEmail("anna@example.com");
        request.setAddress(address);

        CreateUserResponse response = client.createUser(request);

        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getId()).isEqualTo("new-id");
        assertThat(response.getErrorCode()).isNull();
        mockServer.verify();
    }

    @Test
    void shouldGetUsersByCity() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:getUsersByCityRequest xmlns:tns="%s">
                            <tns:city>Warsaw</tns:city>
                        </tns:getUsersByCityRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:getUsersByCityResponse xmlns:tns="%s">
                            <tns:users>
                                <tns:id>id-1</tns:id>
                                <tns:firstName>Jan</tns:firstName>
                                <tns:lastName>Kowalski</tns:lastName>
                                <tns:email>jan@example.com</tns:email>
                                <tns:address>
                                    <tns:street>ul. Testowa 1</tns:street>
                                    <tns:city>Warsaw</tns:city>
                                    <tns:zipCode>00-001</tns:zipCode>
                                    <tns:country>Poland</tns:country>
                                </tns:address>
                            </tns:users>
                            <tns:users>
                                <tns:id>id-2</tns:id>
                                <tns:firstName>Anna</tns:firstName>
                                <tns:lastName>Nowak</tns:lastName>
                                <tns:email>anna@example.com</tns:email>
                                <tns:address>
                                    <tns:street>ul. Kwiatowa 5</tns:street>
                                    <tns:city>Warsaw</tns:city>
                                    <tns:zipCode>00-002</tns:zipCode>
                                    <tns:country>Poland</tns:country>
                                </tns:address>
                            </tns:users>
                        </tns:getUsersByCityResponse>
                        """.formatted(NS))));

        GetUsersByCityResponse response = client.getUsersByCity("Warsaw");

        assertThat(response.getUsers()).hasSize(2);
        assertThat(response.getUsers()).extracting("email")
                .containsExactly("jan@example.com", "anna@example.com");
        mockServer.verify();
    }
}
