package pl.kathelan.soap.push.endpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;
import pl.kathelan.soap.push.domain.PushRecord;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.repository.PushRepository;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

@SpringBootTest
@ActiveProfiles("local")
class MobilePushEndpointIntegrationTest {

    private static final String NS = "http://kathelan.pl/soap/push";
    private static final Map<String, String> NS_MAP = Map.of("tns", NS);

    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String PW_TEXT_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

    @Autowired
    private ApplicationContext context;

    @Autowired
    private PushRepository pushRepository;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(context);
    }

    // ===== getUserCapabilities =====

    @Test
    void shouldReturnCapabilitiesForActiveUser() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:getUserCapabilitiesRequest xmlns:tns="%s">
                    <tns:userId>user-push</tns:userId>
                </tns:getUserCapabilitiesRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:getUserCapabilitiesResponse/tns:userId", NS_MAP).evaluatesTo("user-push"))
                .andExpect(xpath("//tns:getUserCapabilitiesResponse/tns:active", NS_MAP).evaluatesTo("true"))
                .andExpect(xpath("//tns:getUserCapabilitiesResponse/tns:authMethods", NS_MAP).evaluatesTo("PUSH"))
                .andExpect(xpath("//tns:getUserCapabilitiesResponse/tns:errorCode", NS_MAP).doesNotExist());
    }

    @Test
    void shouldReturnMultipleAuthMethodsForUserMulti() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:getUserCapabilitiesRequest xmlns:tns="%s">
                    <tns:userId>user-multi</tns:userId>
                </tns:getUserCapabilitiesRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("count(//tns:getUserCapabilitiesResponse/tns:authMethods)", NS_MAP).evaluatesTo(2))
                .andExpect(xpath("//tns:getUserCapabilitiesResponse/tns:errorCode", NS_MAP).doesNotExist());
    }

    @Test
    void shouldReturnUserNotFoundForUnknownUser() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:getUserCapabilitiesRequest xmlns:tns="%s">
                    <tns:userId>unknown-user</tns:userId>
                </tns:getUserCapabilitiesRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:getUserCapabilitiesResponse/tns:errorCode", NS_MAP).evaluatesTo("USER_NOT_FOUND"))
                .andExpect(xpath("//tns:getUserCapabilitiesResponse/tns:userId", NS_MAP).doesNotExist());
    }

    // ===== sendPush =====

    @Test
    void shouldSendPushSuccessfullyForActiveUser() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:sendPushRequest xmlns:tns="%s">
                    <tns:userId>user-push</tns:userId>
                    <tns:processId>proc-test-1</tns:processId>
                </tns:sendPushRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:sendPushResponse/tns:deliveryId", NS_MAP).exists())
                .andExpect(xpath("//tns:sendPushResponse/tns:sendStatus", NS_MAP).evaluatesTo("SENT"))
                .andExpect(xpath("//tns:sendPushResponse/tns:errorCode", NS_MAP).doesNotExist());
    }

    @Test
    void shouldReturnUserNotFoundWhenSendingPushForUnknownUser() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:sendPushRequest xmlns:tns="%s">
                    <tns:userId>unknown-user</tns:userId>
                    <tns:processId>proc-test-2</tns:processId>
                </tns:sendPushRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:sendPushResponse/tns:errorCode", NS_MAP).evaluatesTo("USER_NOT_FOUND"))
                .andExpect(xpath("//tns:sendPushResponse/tns:deliveryId", NS_MAP).doesNotExist());
    }

    @Test
    void shouldReturnUserInactiveWhenSendingPushForInactiveUser() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:sendPushRequest xmlns:tns="%s">
                    <tns:userId>user-inactive</tns:userId>
                    <tns:processId>proc-test-3</tns:processId>
                </tns:sendPushRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:sendPushResponse/tns:errorCode", NS_MAP).evaluatesTo("USER_INACTIVE"))
                .andExpect(xpath("//tns:sendPushResponse/tns:deliveryId", NS_MAP).doesNotExist());
    }

    // ===== getPushStatus =====

    @Test
    void shouldReturnPendingStatusForExistingDelivery() throws Exception {
        pushRepository.save(buildPushRecord("delivery-status-1", "user-push", "proc-s1", PushStatus.PENDING));

        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:getPushStatusRequest xmlns:tns="%s">
                    <tns:deliveryId>delivery-status-1</tns:deliveryId>
                </tns:getPushStatusRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:getPushStatusResponse/tns:deliveryId", NS_MAP).evaluatesTo("delivery-status-1"))
                .andExpect(xpath("//tns:getPushStatusResponse/tns:pushStatus", NS_MAP).evaluatesTo("PENDING"))
                .andExpect(xpath("//tns:getPushStatusResponse/tns:errorCode", NS_MAP).doesNotExist());
    }

    @Test
    void shouldReturnApprovedStatusAfterUpdate() throws Exception {
        pushRepository.save(buildPushRecord("delivery-status-2", "user-push", "proc-s2", PushStatus.APPROVED));

        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:getPushStatusRequest xmlns:tns="%s">
                    <tns:deliveryId>delivery-status-2</tns:deliveryId>
                </tns:getPushStatusRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:getPushStatusResponse/tns:pushStatus", NS_MAP).evaluatesTo("APPROVED"))
                .andExpect(xpath("//tns:getPushStatusResponse/tns:errorCode", NS_MAP).doesNotExist());
    }

    @Test
    void shouldReturnDeliveryNotFoundForUnknownDeliveryId() throws Exception {
        client.sendRequest(withSoapEnvelope(envelope("""
                <tns:getPushStatusRequest xmlns:tns="%s">
                    <tns:deliveryId>nonexistent-delivery</tns:deliveryId>
                </tns:getPushStatusRequest>
                """.formatted(NS))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:getPushStatusResponse/tns:errorCode", NS_MAP).evaluatesTo("DELIVERY_NOT_FOUND"))
                .andExpect(xpath("//tns:getPushStatusResponse/tns:deliveryId", NS_MAP).doesNotExist());
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

    private PushRecord buildPushRecord(String deliveryId, String userId, String processId, PushStatus status) {
        return PushRecord.builder()
                .deliveryId(deliveryId)
                .userId(userId)
                .processId(processId)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }
}