package pl.kathelan.soap.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.client.WebServiceFaultException;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.client.MobilePushClientImpl;
import pl.kathelan.soap.client.exception.SoapClientException;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushResponse;
import pl.kathelan.soap.push.repository.InMemoryCapabilitiesRepository;
import pl.kathelan.soap.push.repository.InMemoryPushRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MobilePushProducerContractTest — producer-side contract tests for the MobilePush SOAP API.
 *
 * <p>These tests make real HTTP calls from {@link MobilePushClientImpl} to the running
 * {@code soap-service-rest} application (started on a random port). No mocks are used.
 * The full WS-Security stack (WSS4J UsernameToken) is active on both sides.
 *
 * <p>Purpose: verify that soap-service correctly implements the MobilePush SOAP contract
 * that auth-service (as consumer) depends on. Any breaking change in the SOAP API
 * ({@code getUserCapabilities}, {@code sendPush}, {@code getPushStatus}) will be caught
 * here before deployment.
 *
 * <p>The counterpart consumer-side test lives in
 * {@code auth-service/src/test/java/.../contract/MobilePushContractTest.java}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DisplayName("MobilePushProducerContractTest — producer-side contract for auth-service consumer")
class MobilePushProducerContractTest extends SoapContractTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private InMemoryCapabilitiesRepository capabilitiesRepository;

    @Autowired
    private InMemoryPushRepository pushRepository;

    private MobilePushClient client;

    @BeforeEach
    void setUp() throws Exception {
        pushRepository.clear();
        capabilitiesRepository.clear();
        client = new MobilePushClientImpl(buildSecuredTemplate(port));
    }

    // ===== getUserCapabilities =====

    @Nested
    @DisplayName("getUserCapabilities — retrieving auth capabilities for a user")
    class GetUserCapabilities {

        /**
         * Happy path: an active user with a PUSH auth method seeded in the repository
         * must return a response with userId, active=true, and PUSH in authMethods.
         * This is the primary contract auth-service depends on to decide whether to initiate push auth.
         */
        @Test
        @DisplayName("returns userId, active=true and PUSH method for an active user")
        void shouldReturnCapabilities_forActiveUser() {
            capabilitiesRepository.save(UserCapabilities.builder()
                    .userId("user-contract-push")
                    .active(true)
                    .authMethods(List.of(AuthMethod.PUSH))
                    .build());

            GetUserCapabilitiesResponse response = client.getUserCapabilities("user-contract-push");

            assertThat(response.getUserId()).isEqualTo("user-contract-push");
            assertThat(response.isActive()).isTrue();
            assertThat(response.getAuthMethods()).hasSize(1);
            assertThat(response.getAuthMethods().get(0).value()).isEqualTo("PUSH");
            assertThat(response.getErrorCode()).isNull();
        }

        /**
         * Inactive user: the SOAP API must return capabilities with active=false so that
         * auth-service can reject the auth process early without sending a push notification.
         */
        @Test
        @DisplayName("returns active=false for a user seeded as inactive")
        void shouldReturnInactiveCapabilities_forInactiveUser() {
            capabilitiesRepository.save(UserCapabilities.builder()
                    .userId("user-contract-inactive")
                    .active(false)
                    .authMethods(List.of(AuthMethod.PUSH))
                    .build());

            GetUserCapabilitiesResponse response = client.getUserCapabilities("user-contract-inactive");

            assertThat(response.getUserId()).isEqualTo("user-contract-inactive");
            assertThat(response.isActive()).isFalse();
            assertThat(response.getErrorCode()).isNull();
        }

        /**
         * Unknown user: when no capabilities exist for the requested userId, the SOAP API must
         * return errorCode=USER_NOT_FOUND. The client translates this into a {@link SoapClientException}.
         * auth-service relies on this contract to surface unknown-user errors upstream.
         */
        @Test
        @DisplayName("throws SoapClientException with USER_NOT_FOUND for an unknown userId")
        void shouldThrowSoapClientException_forUnknownUser() {
            assertThatThrownBy(() -> client.getUserCapabilities("unknown-user-contract"))
                    .isInstanceOf(SoapClientException.class)
                    .hasMessageContaining("USER_NOT_FOUND");
        }

        /**
         * Missing WS-Security header: any request without a valid WS-Security UsernameToken
         * must be rejected with a SOAP fault. This prevents unauthenticated consumers from
         * accessing the MobilePush SOAP API.
         */
        @Test
        @DisplayName("throws WebServiceFaultException when WS-Security header is absent")
        void shouldRejectRequest_whenWsSecurityHeaderAbsent() throws Exception {
            MobilePushClient unauthClient = new MobilePushClientImpl(buildUnsecuredTemplate(port));

            assertThatThrownBy(() -> unauthClient.getUserCapabilities("any"))
                    .isInstanceOf(WebServiceFaultException.class);
        }
    }

    // ===== sendPush =====

    @Nested
    @DisplayName("sendPush — sending a push notification for an auth process")
    class SendPush {

        /**
         * Happy path: for an active user, sendPush must return a non-null deliveryId and
         * sendStatus=SENT. The deliveryId is the correlation key auth-service uses to poll
         * getPushStatus — so it must be a stable, non-empty string in every successful response.
         */
        @Test
        @DisplayName("returns non-null deliveryId and SENT status for an active user")
        void shouldReturnDeliveryIdAndSentStatus_forActiveUser() {
            capabilitiesRepository.save(UserCapabilities.builder()
                    .userId("user-contract-send")
                    .active(true)
                    .authMethods(List.of(AuthMethod.PUSH))
                    .build());

            SendPushResponse response = client.sendPush("user-contract-send", "proc-contract-1");

            assertThat(response.getDeliveryId()).isNotEmpty();
            assertThat(response.getSendStatus().value()).isEqualTo("SENT");
            assertThat(response.getExpiresAt()).isNotNull();
            assertThat(response.getErrorCode()).isNull();
        }

        /**
         * Unknown user: sending a push for a userId with no capabilities must throw
         * SoapClientException with USER_NOT_FOUND. auth-service treats this as a
         * configuration error (user enrolled in auth-service but not in soap-service).
         */
        @Test
        @DisplayName("throws SoapClientException with USER_NOT_FOUND when user has no capabilities")
        void shouldThrowSoapClientException_forUnknownUser() {
            assertThatThrownBy(() -> client.sendPush("ghost-user", "proc-contract-2"))
                    .isInstanceOf(SoapClientException.class)
                    .hasMessageContaining("USER_NOT_FOUND");
        }

        /**
         * Inactive user: sending a push for an inactive user must throw SoapClientException with
         * USER_INACTIVE. auth-service handles this by rejecting the initProcess request early.
         */
        @Test
        @DisplayName("throws SoapClientException with USER_INACTIVE for an inactive user")
        void shouldThrowSoapClientException_forInactiveUser() {
            capabilitiesRepository.save(UserCapabilities.builder()
                    .userId("user-contract-inactive-send")
                    .active(false)
                    .authMethods(List.of(AuthMethod.PUSH))
                    .build());

            assertThatThrownBy(() -> client.sendPush("user-contract-inactive-send", "proc-contract-3"))
                    .isInstanceOf(SoapClientException.class)
                    .hasMessageContaining("USER_INACTIVE");
        }
    }

    // ===== getPushStatus =====

    @Nested
    @DisplayName("getPushStatus — polling the delivery status of a sent push notification")
    class GetPushStatus {

        /**
         * PENDING status: immediately after sendPush, getPushStatus must return PENDING.
         * This is the initial status auth-service sees on the first poll after initProcess.
         */
        @Test
        @DisplayName("returns PENDING status immediately after sendPush")
        void shouldReturnPending_immediatelyAfterSendPush() {
            capabilitiesRepository.save(UserCapabilities.builder()
                    .userId("user-contract-poll")
                    .active(true)
                    .authMethods(List.of(AuthMethod.PUSH))
                    .build());

            SendPushResponse sent = client.sendPush("user-contract-poll", "proc-poll-1");
            String deliveryId = sent.getDeliveryId();

            GetPushStatusResponse response = client.getPushStatus(deliveryId);

            assertThat(response.getDeliveryId()).isEqualTo(deliveryId);
            assertThat(response.getPushStatus().value()).isEqualTo("PENDING");
            assertThat(response.getErrorCode()).isNull();
        }

        /**
         * APPROVED status: after the simulator (or a real device) sets the status to APPROVED,
         * getPushStatus must return APPROVED. auth-service transitions the auth process to
         * ProcessState.APPROVED on this response.
         */
        @Test
        @DisplayName("returns APPROVED status after the push record is approved")
        void shouldReturnApproved_afterStatusIsSetToApproved() {
            capabilitiesRepository.save(UserCapabilities.builder()
                    .userId("user-contract-approved")
                    .active(true)
                    .authMethods(List.of(AuthMethod.PUSH))
                    .build());

            String deliveryId = client.sendPush("user-contract-approved", "proc-approved-1").getDeliveryId();
            pushRepository.updateStatus(deliveryId, PushStatus.APPROVED);

            GetPushStatusResponse response = client.getPushStatus(deliveryId);

            assertThat(response.getPushStatus().value()).isEqualTo("APPROVED");
        }

        /**
         * REJECTED status: when the user rejects the push on their device, getPushStatus must
         * return REJECTED. auth-service transitions the auth process to ProcessState.REJECTED.
         */
        @Test
        @DisplayName("returns REJECTED status after the push record is rejected")
        void shouldReturnRejected_afterStatusIsSetToRejected() {
            capabilitiesRepository.save(UserCapabilities.builder()
                    .userId("user-contract-rejected")
                    .active(true)
                    .authMethods(List.of(AuthMethod.PUSH))
                    .build());

            String deliveryId = client.sendPush("user-contract-rejected", "proc-rejected-1").getDeliveryId();
            pushRepository.updateStatus(deliveryId, PushStatus.REJECTED);

            GetPushStatusResponse response = client.getPushStatus(deliveryId);

            assertThat(response.getPushStatus().value()).isEqualTo("REJECTED");
        }

        /**
         * EXPIRED status: when the push notification times out on the device side, getPushStatus
         * must return EXPIRED. auth-service uses this to transition to ProcessState.EXPIRED.
         */
        @Test
        @DisplayName("returns EXPIRED status after the push record expires")
        void shouldReturnExpired_afterStatusIsSetToExpired() {
            capabilitiesRepository.save(UserCapabilities.builder()
                    .userId("user-contract-expired")
                    .active(true)
                    .authMethods(List.of(AuthMethod.PUSH))
                    .build());

            String deliveryId = client.sendPush("user-contract-expired", "proc-expired-1").getDeliveryId();
            pushRepository.updateStatus(deliveryId, PushStatus.EXPIRED);

            GetPushStatusResponse response = client.getPushStatus(deliveryId);

            assertThat(response.getPushStatus().value()).isEqualTo("EXPIRED");
        }

        /**
         * Unknown deliveryId: querying a deliveryId that was never created (or already cleared)
         * must throw SoapClientException with DELIVERY_NOT_FOUND. auth-service treats this as a
         * fatal inconsistency — the process cannot be polled further.
         */
        @Test
        @DisplayName("throws SoapClientException with DELIVERY_NOT_FOUND for an unknown deliveryId")
        void shouldThrowSoapClientException_forUnknownDeliveryId() {
            assertThatThrownBy(() -> client.getPushStatus("ghost-delivery-id"))
                    .isInstanceOf(SoapClientException.class)
                    .hasMessageContaining("DELIVERY_NOT_FOUND");
        }
    }
}
