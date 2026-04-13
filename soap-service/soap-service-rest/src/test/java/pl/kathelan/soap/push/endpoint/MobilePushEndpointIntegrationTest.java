package pl.kathelan.soap.push.endpoint;

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
import pl.kathelan.soap.push.domain.AccountStatus;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.PushRecord;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;
import pl.kathelan.soap.push.repository.CapabilitiesRepository;
import pl.kathelan.soap.push.repository.PushRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.ws.test.server.RequestCreators.withSoapEnvelope;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

/**
 * MobilePushEndpoint — integration tests for SOAP push notification operations.
 *
 * <p>Covers three operations exposed by {@code MobilePushEndpoint}:
 * {@code getUserCapabilities}, {@code sendPush}, and {@code getPushStatus}.
 *
 * <p>Uses {@link MockWebServiceClient} against a full Spring-WS context (profile {@code local}).
 * Each test group seeds its own state via in-memory repositories to remain independent.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("MobilePushEndpoint — SOAP push notification operations integration")
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
    private CapabilitiesRepository capabilitiesRepository;

    @Autowired
    private PushRepository pushRepository;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(context);
        seedCapabilities();
    }

    private void seedCapabilities() {
        capabilitiesRepository.save(UserCapabilities.builder()
                .userId("user-push")
                .active(true)
                .accountStatus(AccountStatus.ACTIVE)
                .authMethods(List.of(AuthMethod.PUSH))
                .devices(List.of())
                .build());

        capabilitiesRepository.save(UserCapabilities.builder()
                .userId("user-multi")
                .active(true)
                .accountStatus(AccountStatus.ACTIVE)
                .authMethods(List.of(AuthMethod.PUSH, AuthMethod.SMS))
                .devices(List.of())
                .build());

        capabilitiesRepository.save(UserCapabilities.builder()
                .userId("user-inactive")
                .active(false)
                .accountStatus(AccountStatus.SUSPENDED)
                .authMethods(List.of(AuthMethod.PUSH))
                .devices(List.of())
                .build());
    }

    // ===== getUserCapabilities =====

    @Nested
    @DisplayName("getUserCapabilities — reading user auth capabilities")
    class GetUserCapabilities {

        /**
         * Happy path for an active user: the response must contain the correct userId,
         * the active flag set to true, and the PUSH auth method. No errorCode expected.
         */
        @Test
        @DisplayName("returns active flag and PUSH auth method for an active user")
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

        /**
         * Multiple auth methods: when a user is enrolled in both PUSH and SMS,
         * the response must list exactly two authMethods elements.
         */
        @Test
        @DisplayName("returns two authMethods elements for a user enrolled in PUSH and SMS")
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

        /**
         * Unknown user: requesting capabilities for a userId that does not exist
         * must return {@code USER_NOT_FOUND} error code and no userId element,
         * rather than an uncaught exception or empty payload.
         */
        @Test
        @DisplayName("returns USER_NOT_FOUND and no userId for an unknown userId")
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
    }

    // ===== sendPush =====

    @Nested
    @DisplayName("sendPush — sending a push notification to a user")
    class SendPush {

        /**
         * Happy path: sending a push to an active user must return a generated deliveryId,
         * sendStatus SENT, and no errorCode.
         */
        @Test
        @DisplayName("returns deliveryId and SENT status for an active user")
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

        /**
         * Unknown user: attempting to send a push to a userId that has no capabilities record
         * must return {@code USER_NOT_FOUND} error code and no deliveryId.
         */
        @Test
        @DisplayName("returns USER_NOT_FOUND and no deliveryId when userId is unknown")
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

        /**
         * Inactive user: sending a push to a suspended/inactive user must return
         * {@code USER_INACTIVE} error code and no deliveryId.
         * This guards against delivering notifications to deactivated accounts.
         */
        @Test
        @DisplayName("returns USER_INACTIVE and no deliveryId for a suspended user")
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
    }

    // ===== getPushStatus =====

    @Nested
    @DisplayName("getPushStatus — polling the delivery status of a push notification")
    class GetPushStatus {

        /**
         * Pending delivery: polling the status of a push record that is still pending
         * must return the correct deliveryId and status PENDING, without an errorCode.
         */
        @Test
        @DisplayName("returns PENDING status for an existing delivery in PENDING state")
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

        /**
         * Approved delivery: once a push record has been updated to APPROVED (e.g. by the simulator),
         * getPushStatus must reflect the new status correctly.
         */
        @Test
        @DisplayName("returns APPROVED status after the push record was updated to APPROVED")
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

        /**
         * Unknown deliveryId: querying the status of a delivery that was never created
         * must return {@code DELIVERY_NOT_FOUND} error code and no deliveryId element.
         */
        @Test
        @DisplayName("returns DELIVERY_NOT_FOUND and no deliveryId for an unknown deliveryId")
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
