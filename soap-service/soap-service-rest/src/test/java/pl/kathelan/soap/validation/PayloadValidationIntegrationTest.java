package pl.kathelan.soap.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;

@SpringBootTest
@ActiveProfiles("local")
class PayloadValidationIntegrationTest {

    private static final String NS = "http://kathelan.pl/soap/users";
    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String PW_TEXT_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

    @Autowired
    private ApplicationContext context;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(context);
    }

    @Test
    void shouldRejectCreateUserWithInvalidEmail() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:createUserRequest xmlns:tns="%s">
                    <tns:firstName>Jan</tns:firstName>
                    <tns:lastName>Kowalski</tns:lastName>
                    <tns:email>not-an-email</tns:email>
                    <tns:address>
                        <tns:street>ul. Testowa 1</tns:street>
                        <tns:city>Warsaw</tns:city>
                        <tns:zipCode>00-001</tns:zipCode>
                        <tns:country>Poland</tns:country>
                    </tns:address>
                </tns:createUserRequest>
                """.formatted(NS))))
                .andExpect(clientOrSenderFault());
    }

    @Test
    void shouldRejectCreateUserWithEmptyFirstName() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:createUserRequest xmlns:tns="%s">
                    <tns:firstName></tns:firstName>
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
                .andExpect(clientOrSenderFault());
    }

    @Test
    void shouldRejectCreateUserWithEmptyCity() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:createUserRequest xmlns:tns="%s">
                    <tns:firstName>Jan</tns:firstName>
                    <tns:lastName>Kowalski</tns:lastName>
                    <tns:email>jan@example.com</tns:email>
                    <tns:address>
                        <tns:street>ul. Testowa 1</tns:street>
                        <tns:city></tns:city>
                        <tns:zipCode>00-001</tns:zipCode>
                        <tns:country>Poland</tns:country>
                    </tns:address>
                </tns:createUserRequest>
                """.formatted(NS))))
                .andExpect(clientOrSenderFault());
    }

    @Test
    void shouldRejectGetUserWithEmptyId() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:getUserRequest xmlns:tns="%s">
                    <tns:id></tns:id>
                </tns:getUserRequest>
                """.formatted(NS))))
                .andExpect(clientOrSenderFault());
    }

    @Test
    void shouldRejectGetUsersByCityWithEmptyCity() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:getUsersByCityRequest xmlns:tns="%s">
                    <tns:city></tns:city>
                </tns:getUsersByCityRequest>
                """.formatted(NS))))
                .andExpect(clientOrSenderFault());
    }

    @Test
    void shouldAcceptValidCreateUserRequest() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
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
                .andExpect(noFault());
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
}
