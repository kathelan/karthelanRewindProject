package pl.kathelan.auth.e2e;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.kathelan.auth.AuthServiceBaseTest;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.auth.service.AuthProcessSchedulerService;
import pl.kathelan.soap.push.generated.PushStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthProcessE2ETest extends AuthServiceBaseTest {

    @Autowired
    private AuthProcessSchedulerService schedulerService;

    @Autowired
    private InMemoryAuthProcessRepository repository;

    // --- PENDING → APPROVED flow ---

    @Test
    void fullFlow_pendingToApproved() throws Exception {
        stubSendPush("delivery-1");
        String processId = initProcess("user1");

        stubGetPushStatus("delivery-1", PushStatus.APPROVED);
        schedulerService.pollAndUpdatePushStatuses();

        assertProcessState(processId, ProcessState.APPROVED);
    }

    // --- PENDING → REJECTED flow ---

    @Test
    void fullFlow_pendingToRejected() throws Exception {
        stubSendPush("delivery-1");
        String processId = initProcess("user1");

        stubGetPushStatus("delivery-1", PushStatus.REJECTED);
        schedulerService.pollAndUpdatePushStatuses();

        assertProcessState(processId, ProcessState.REJECTED);
    }

    // --- PENDING → EXPIRED via push status ---

    @Test
    void fullFlow_pendingToExpiredViaPushStatus() throws Exception {
        stubSendPush("delivery-1");
        String processId = initProcess("user1");

        stubGetPushStatus("delivery-1", PushStatus.EXPIRED);
        schedulerService.pollAndUpdatePushStatuses();

        assertProcessState(processId, ProcessState.EXPIRED);
    }

    // --- PENDING → EXPIRED via local TTL ---

    @Test
    void fullFlow_pendingToExpiredViaLocalTtl() throws Exception {
        stubSendPush("delivery-1", java.time.LocalDateTime.now().minusMinutes(1));
        String processId = initProcess("user1");

        schedulerService.expireOverdueProcesses();

        assertProcessState(processId, ProcessState.EXPIRED);
    }

    // --- PENDING → CANCELLED → cannot transition further ---

    @Test
    void fullFlow_cancelThenRejectHasNoEffect() throws Exception {
        stubSendPush("delivery-1");
        String processId = initProcess("user1");

        doPatch("/process/{id}/cancel", processId)
                .expectStatus().isNoContent();

        // Scheduler runs but process is already terminal — no state change
        stubGetPushStatus("delivery-1", PushStatus.APPROVED);
        schedulerService.pollAndUpdatePushStatuses();

        assertProcessState(processId, ProcessState.CANCELLED);
    }

    // --- CLOSED: new init closes previous PENDING ---

    @Test
    void fullFlow_newInitClosesPreviousPending() throws Exception {
        stubSendPush("delivery-1");
        String firstProcessId = initProcess("user1");

        stubSendPush("delivery-2");
        String secondProcessId = initProcess("user1");

        assertProcessState(firstProcessId, ProcessState.CLOSED);
        assertProcessState(secondProcessId, ProcessState.PENDING);
    }

    // --- helper ---

    private String initProcess(String userId) throws Exception {
        byte[] body = doPost("/process/init", new InitProcessRequest(userId, AuthMethod.PUSH))
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        return objectMapper.readTree(body).at("/data/processId").asText();
    }

    private void assertProcessState(String processId, ProcessState expected) {
        repository.findById(UUID.fromString(processId))
                .ifPresentOrElse(
                        p -> assertThat(p.processState()).isEqualTo(expected),
                        () -> org.junit.jupiter.api.Assertions.fail("Process not found: " + processId)
                );
    }
}
