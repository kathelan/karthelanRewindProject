package pl.kathelan.soap.validation;

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

import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;

/**
 * PayloadValidationIntegrationTest — integration tests for XSD schema validation on inbound requests.
 *
 * <p>Spring-WS validates incoming SOAP message payloads against the {@code users.xsd} schema.
 * Invalid payloads (missing required fields, constraint violations such as bad email format)
 * must result in a client/sender SOAP Fault before the endpoint handler is invoked.
 *
 * <p>Valid requests must pass through without a fault. Profile {@code local} activates
 * in-memory repositories; no database or external service is needed.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("PayloadValidationIntegrationTest — XSD schema validation on inbound SOAP messages")
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

    // ===== createUser validation =====

    @Nested
    @DisplayName("createUser — validating required and formatted fields")
    class CreateUserValidation {

        /**
         * Invalid email format: the XSD pattern constraint on the email field must reject
         * any value that is not a valid email address. The response must be a client/sender fault.
         */
        @Test
        @DisplayName("rejects createUser with a malformed email (not-an-email)")
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

        /**
         * Empty firstName: the XSD minLength constraint must reject a createUser request
         * with an empty firstName element before the endpoint processes the payload.
         */
        @Test
        @DisplayName("rejects createUser when firstName is empty")
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

        /**
         * Empty city in address: the XSD constraint on address/city must reject a request
         * where the city element is present but blank.
         */
        @Test
        @DisplayName("rejects createUser when address city is empty")
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

        /**
         * Valid request: a createUser payload that satisfies all XSD constraints must
         * pass validation and result in no fault — the endpoint receives the request normally.
         */
        @Test
        @DisplayName("accepts a fully valid createUser request without a fault")
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
    }

    // ===== getUser validation =====

    @Nested
    @DisplayName("getUser — validating the id field")
    class GetUserValidation {

        /**
         * Empty id: the XSD minLength constraint on the id field must reject a getUser
         * request with a blank id element, preventing the endpoint from querying with an empty key.
         */
        @Test
        @DisplayName("rejects getUser when id is empty")
        void shouldRejectGetUserWithEmptyId() throws Exception {
            client.sendRequest(withSoapEnvelope(envelope("""
                    <tns:getUserRequest xmlns:tns="%s">
                        <tns:id></tns:id>
                    </tns:getUserRequest>
                    """.formatted(NS))))
                    .andExpect(clientOrSenderFault());
        }
    }

    // ===== getUsersByCity validation =====

    @Nested
    @DisplayName("getUsersByCity — validating the city field")
    class GetUsersByCityValidation {

        /**
         * Empty city: the XSD minLength constraint must reject a getUsersByCity request
         * with a blank city element so the repository is never queried with an empty key.
         */
        @Test
        @DisplayName("rejects getUsersByCity when city is empty")
        void shouldRejectGetUsersByCityWithEmptyCity() throws Exception {
            client.sendRequest(withSoapEnvelope(envelope("""
                    <tns:getUsersByCityRequest xmlns:tns="%s">
                        <tns:city></tns:city>
                    </tns:getUsersByCityRequest>
                    """.formatted(NS))))
                    .andExpect(clientOrSenderFault());
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
}
