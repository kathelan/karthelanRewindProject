package pl.kathelan.auth.simulator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.auth.service.AuthProcessSchedulerService;
import pl.kathelan.soap.client.simulator.SimulatorClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests: auth-service talks to a REAL soap-service (local+simulator profiles).
 * No @MockitoBean — MobilePushClient uses actual HTTP/SOAP.
 * Push statuses and capabilities are controlled via SimulatorClient.
 *
 * Covers all terminal states: APPROVED, REJECTED, EXPIRED (push), EXPIRED (TTL), CANCELLED, CLOSED.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "soap.client.username=admin",
        "soap.client.password=adminpass"
})
class AuthServiceSimulatorContractTest {

    @RegisterExtension
    static SoapSimulatorExtension soapSimulator = new SoapSimulatorExtension();

    @DynamicPropertySource
    static void overrideSoapUrl(DynamicPropertyRegistry registry) {
        registry.add("soap.client.url", soapSimulator::getSoapUrl);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private InMemoryAuthProcessRepository authRepository;

    @Autowired
    private AuthProcessSchedulerService schedulerService;

    private SimulatorClient simulator;

    @BeforeEach
    void setup() {
        authRepository.clear();
        simulator = new SimulatorClient(soapSimulator.getSimulatorBaseUrl());
        simulator.reset();
        simulator.seedCapabilities("user-e2e", true, List.of("PUSH"));
    }

    // --- PENDING: init creates process in PENDING state ---

    @Test
    void init_createsPendingProcess() {
        String processId = initProcess("user-e2e");

        assertProcessState(processId, ProcessState.PENDING);
    }

    @Test
    void init_responseContainsProcessIdAndExpiresAt() {
        webTestClient.post().uri("/process/init")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new InitProcessRequest("user-e2e", AuthMethod.PUSH))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.processId").isNotEmpty()
                .jsonPath("$.data.expiresAt").isNotEmpty();
    }

    @Test
    void init_expiresAtIsInTheFuture() {
        byte[] body = webTestClient.post().uri("/process/init")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new InitProcessRequest("user-e2e", AuthMethod.PUSH))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();

        String expiresAtRaw = parseJson(body, "/data/expiresAt");
        LocalDateTime expiresAt = LocalDateTime.parse(expiresAtRaw);
        assertThat(expiresAt).isAfter(LocalDateTime.now());
    }

    // --- APPROVED: scheduler polls soap-service, gets APPROVED ---

    @Test
    void fullFlow_pendingToApproved() {
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        simulator.setPushStatus(deliveryId, "APPROVED");
        schedulerService.pollAndUpdatePushStatuses();

        assertProcessState(processId, ProcessState.APPROVED);
    }

    @Test
    void approved_isTerminal_cancelReturnConflict() {
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        simulator.setPushStatus(deliveryId, "APPROVED");
        schedulerService.pollAndUpdatePushStatuses();

        webTestClient.patch().uri("/process/{id}/cancel", processId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("INVALID_STATE_TRANSITION");
    }

    // --- REJECTED: scheduler polls soap-service, gets REJECTED ---

    @Test
    void fullFlow_pendingToRejected() {
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        simulator.setPushStatus(deliveryId, "REJECTED");
        schedulerService.pollAndUpdatePushStatuses();

        assertProcessState(processId, ProcessState.REJECTED);
    }

    // --- EXPIRED via push status ---

    @Test
    void fullFlow_pendingToExpiredViaPushStatus() {
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        simulator.setPushStatus(deliveryId, "EXPIRED");
        schedulerService.pollAndUpdatePushStatuses();

        assertProcessState(processId, ProcessState.EXPIRED);
    }

    // --- EXPIRED via local TTL (expiresAt in the past) ---

    @Test
    void fullFlow_pendingToExpiredViaLocalTtl() {
        // Inject a PENDING process with past expiresAt directly — no SOAP call needed
        String processId = injectExpiredProcess("user-ttl");

        schedulerService.expireOverdueProcesses();

        assertProcessState(processId, ProcessState.EXPIRED);
    }

    // --- CANCELLED: client cancels pending process ---

    @Test
    void fullFlow_pendingToCancelled() {
        String processId = initProcess("user-e2e");

        webTestClient.patch().uri("/process/{id}/cancel", processId)
                .exchange()
                .expectStatus().isNoContent();

        assertProcessState(processId, ProcessState.CANCELLED);
    }

    @Test
    void cancelled_schedulerHasNoEffect() {
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        webTestClient.patch().uri("/process/{id}/cancel", processId)
                .exchange().expectStatus().isNoContent();

        simulator.setPushStatus(deliveryId, "APPROVED");
        schedulerService.pollAndUpdatePushStatuses();

        // Already terminal — scheduler must not change state
        assertProcessState(processId, ProcessState.CANCELLED);
    }

    @Test
    void cancel_returns404ForUnknownProcess() {
        webTestClient.patch().uri("/process/{id}/cancel", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.errorCode").isEqualTo("PROCESS_NOT_FOUND");
    }

    @Test
    void cancel_returns409WhenAlreadyCancelled() {
        String processId = initProcess("user-e2e");

        webTestClient.patch().uri("/process/{id}/cancel", processId)
                .exchange().expectStatus().isNoContent();

        webTestClient.patch().uri("/process/{id}/cancel", processId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.errorCode").isEqualTo("INVALID_STATE_TRANSITION");
    }

    // --- CLOSED: new init closes previous PENDING ---

    @Test
    void fullFlow_newInitClosesPreviousPending() {
        String firstProcessId = initProcess("user-e2e");
        String secondProcessId = initProcess("user-e2e");

        assertProcessState(firstProcessId, ProcessState.CLOSED);
        assertProcessState(secondProcessId, ProcessState.PENDING);
    }

    @Test
    void closed_schedulerHasNoEffect() {
        String firstProcessId = initProcess("user-e2e");
        String firstDeliveryId = getDeliveryId(firstProcessId);

        initProcess("user-e2e"); // closes first

        simulator.setPushStatus(firstDeliveryId, "APPROVED");
        schedulerService.pollAndUpdatePushStatuses();

        // CLOSED is terminal — scheduler must not change state
        assertProcessState(firstProcessId, ProcessState.CLOSED);
    }

    // --- SSE stream ---

    @Test
    void stream_returns200ForPendingProcess() {
        String processId = initProcess("user-e2e");

        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void stream_returns404ForUnknownProcess() {
        webTestClient.get().uri("/process/{id}/stream", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.errorCode").isEqualTo("PROCESS_NOT_FOUND");
    }

    // SSE event content: terminal process closes stream immediately → body is readable
    @Test
    void stream_sendsApprovedEventAndClosesImmediately() {
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        simulator.setPushStatus(deliveryId, "APPROVED");
        schedulerService.pollAndUpdatePushStatuses();

        // Frontend reconnects after state change (or page reload)
        // → terminal state: one event sent, stream closes
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("APPROVED")
                        .doesNotContain("CANCEL"));
    }

    @Test
    void stream_sendsCancelledEventAndClosesImmediately() {
        String processId = initProcess("user-e2e");

        webTestClient.patch().uri("/process/{id}/cancel", processId)
                .exchange().expectStatus().isNoContent();

        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("CANCELLED")
                        .doesNotContain("\"CANCEL\""));
    }

    @Test
    void stream_sendsRejectedEventAndClosesImmediately() {
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        simulator.setPushStatus(deliveryId, "REJECTED");
        schedulerService.pollAndUpdatePushStatuses();

        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("REJECTED"));
    }

    @Test
    void stream_sendsExpiredEventAndClosesImmediately() {
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        simulator.setPushStatus(deliveryId, "EXPIRED");
        schedulerService.pollAndUpdatePushStatuses();

        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("EXPIRED"));
    }

    @Test
    void stream_sendsClosedEventAndClosesImmediately() {
        String firstProcessId = initProcess("user-e2e");
        initProcess("user-e2e"); // closes first

        webTestClient.get().uri("/process/{id}/stream", firstProcessId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("CLOSED"));
    }

    // --- capabilities ---

    @Test
    void capabilities_returnsSeededUser() {
        simulator.seedCapabilities("user-caps", true, List.of("PUSH", "SMS"));

        webTestClient.get().uri("/auth/capabilities/{userId}", "user-caps")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("user-caps")
                .jsonPath("$.data.active").isEqualTo(true)
                .jsonPath("$.data.authMethods.length()").isEqualTo(2);
    }

    @Test
    void init_failsWhenUserInactive() {
        simulator.seedCapabilities("user-inactive-e2e", false, List.of("PUSH"));

        webTestClient.post().uri("/process/init")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new InitProcessRequest("user-inactive-e2e", AuthMethod.PUSH))
                .exchange()
                .expectStatus().is5xxServerError();
    }

    // ============================================================
    // Frontend journey tests — simulate complete client flow
    // for each terminal state (capabilities → init → stream → result)
    // ============================================================

    @Test
    void frontendJourney_approved() {
        // 1. Frontend checks what auth methods the user has
        webTestClient.get().uri("/auth/capabilities/{userId}", "user-e2e")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.active").isEqualTo(true)
                .jsonPath("$.data.authMethods[0]").isEqualTo("PUSH");

        // 2. Frontend initiates auth process — gets processId + expiresAt for countdown timer
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);
        assertThat(authRepository.findById(UUID.fromString(processId)))
                .hasValueSatisfying(p -> assertThat(p.expiresAt()).isAfter(LocalDateTime.now()));

        // 3. Frontend subscribes to SSE stream — process is PENDING, stream stays open
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();

        // 4. User approves push on mobile — scheduler picks it up
        simulator.setPushStatus(deliveryId, "APPROVED");
        schedulerService.pollAndUpdatePushStatuses();
        assertProcessState(processId, ProcessState.APPROVED);

        // 5. Frontend reconnects (or SSE pushed update) — gets APPROVED, no actions, stream closes
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("APPROVED")
                        .doesNotContain("CANCEL"));
    }

    @Test
    void frontendJourney_rejected() {
        // 1. Capabilities
        webTestClient.get().uri("/auth/capabilities/{userId}", "user-e2e")
                .exchange().expectStatus().isOk();

        // 2. Init
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        // 3. Subscribe to SSE stream
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk();

        // 4. User rejects push — scheduler picks it up
        simulator.setPushStatus(deliveryId, "REJECTED");
        schedulerService.pollAndUpdatePushStatuses();
        assertProcessState(processId, ProcessState.REJECTED);

        // 5. Frontend shows rejection — reconnects stream, gets REJECTED, stream closes
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody()).contains("REJECTED"));
    }

    @Test
    void frontendJourney_expiredViaPushTimeout() {
        // 1. Capabilities
        webTestClient.get().uri("/auth/capabilities/{userId}", "user-e2e")
                .exchange().expectStatus().isOk();

        // 2. Init
        String processId = initProcess("user-e2e");
        String deliveryId = getDeliveryId(processId);

        // 3. Subscribe to SSE stream
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk();

        // 4. Push expires on mobile side — scheduler picks it up
        simulator.setPushStatus(deliveryId, "EXPIRED");
        schedulerService.pollAndUpdatePushStatuses();
        assertProcessState(processId, ProcessState.EXPIRED);

        // 5. Frontend shows timeout — reconnects stream, gets EXPIRED, stream closes
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody()).contains("EXPIRED"));
    }

    @Test
    void frontendJourney_expiredViaLocalTtl() {
        // Process created directly with past expiresAt (simulates soap-service returning past TTL)
        String processId = injectExpiredProcess("user-ttl-journey");

        // Scheduler expires it
        schedulerService.expireOverdueProcesses();
        assertProcessState(processId, ProcessState.EXPIRED);

        // Frontend reconnects — gets EXPIRED, stream closes
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody()).contains("EXPIRED"));
    }

    @Test
    void frontendJourney_cancelledByUser() {
        // 1. Capabilities
        webTestClient.get().uri("/auth/capabilities/{userId}", "user-e2e")
                .exchange().expectStatus().isOk();

        // 2. Init
        String processId = initProcess("user-e2e");

        // 3. Subscribe to SSE stream — PENDING
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk();

        // 4. User clicks "Cancel" in the UI
        webTestClient.patch().uri("/process/{id}/cancel", processId)
                .exchange()
                .expectStatus().isNoContent();

        assertProcessState(processId, ProcessState.CANCELLED);

        // 5. Frontend reconnects — gets CANCELLED, no actions, stream closes
        webTestClient.get().uri("/process/{id}/stream", processId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody())
                        .contains("CANCELLED")
                        .doesNotContain("\"CANCEL\""));
    }

    @Test
    void frontendJourney_closedByNewInitFromSameUser() {
        // 1. User had a previous pending auth (e.g., from another device/tab)
        String firstProcessId = initProcess("user-e2e");
        assertProcessState(firstProcessId, ProcessState.PENDING);

        // 2. User starts a new auth — previous is automatically closed
        String secondProcessId = initProcess("user-e2e");
        assertProcessState(firstProcessId, ProcessState.CLOSED);
        assertProcessState(secondProcessId, ProcessState.PENDING);

        // 3. First process stream reconnect — gets CLOSED, stream closes
        webTestClient.get().uri("/process/{id}/stream", firstProcessId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody()).contains("CLOSED"));

        // 4. Second process is still active — stream stays open
        webTestClient.get().uri("/process/{id}/stream", secondProcessId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk();
    }

    // --- helpers ---

    private String initProcess(String userId) {
        byte[] body = webTestClient.post().uri("/process/init")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new InitProcessRequest(userId, AuthMethod.PUSH))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return parseJson(body, "/data/processId");
    }

    private String getDeliveryId(String processId) {
        return authRepository.findById(UUID.fromString(processId))
                .orElseThrow(() -> new AssertionError("Process not found: " + processId))
                .deliveryId();
    }

    private void assertProcessState(String processId, ProcessState expected) {
        assertThat(authRepository.findById(UUID.fromString(processId)))
                .hasValueSatisfying(p -> assertThat(p.processState())
                        .as("Process %s should be %s", processId, expected)
                        .isEqualTo(expected));
    }

    private String injectExpiredProcess(String userId) {
        // Directly insert a PENDING process with past expiresAt — bypasses SOAP.
        // The soap-service always returns expiresAt=now+2min so we can't control this via simulator.
        var process = pl.kathelan.auth.domain.AuthProcess
                .create(userId, AuthMethod.PUSH)
                .assignDelivery("delivery-ttl-" + userId, LocalDateTime.now().minusMinutes(5));
        authRepository.save(process);
        return process.id().toString();
    }

    private String parseJson(byte[] body, String jsonPointer) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(body).at(jsonPointer).asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
