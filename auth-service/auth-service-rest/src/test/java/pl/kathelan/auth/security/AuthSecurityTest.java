package pl.kathelan.auth.security;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import pl.kathelan.auth.AuthServiceBaseTest;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.InitProcessRequest;

import java.util.UUID;

/**
 * Security tests for the auth process API.
 * <p>
 * Current IDOR state: process ownership is NOT enforced — any caller with a valid processId
 * (UUID) can cancel or read any process. The only protection is UUID non-guessability.
 * <p>
 * Full IDOR protection requires Bearer token authentication (tracked as future work).
 */
class AuthSecurityTest extends AuthServiceBaseTest {

    @Test
    void processId_isUuidFormat_preventsEnumeration() throws Exception {
        stubSendPush("delivery-1");

        byte[] body = doPost("/process/init", new InitProcessRequest("user1", AuthMethod.PUSH))
                .expectStatus().isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();

        String processId = objectMapper.readTree(body).at("/data/processId").asText();

        // Verify processId is a valid UUID (unguessable, non-sequential)
        UUID parsed = UUID.fromString(processId);
        org.assertj.core.api.Assertions.assertThat(parsed).isNotNull();
    }

    @Test
    void cancelProcess_doesNotAffectOtherUsersProcess() throws Exception {
        stubSendPush("delivery-1");
        String process1 = initProcess("user1");

        stubSendPush("delivery-2");
        String process2 = initProcess("user2");

        doPatch("/process/{id}/cancel", process1)
                .expectStatus().isNoContent();

        // user2's process is completely unaffected
        doGetSse("/process/{id}/stream", process2)
                .expectStatus().isOk();
    }

    @Test
    void unknownProcessId_returns404_notLeakingOtherProcesses() {
        doGet("/process/{id}/stream", UUID.randomUUID())
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("PROCESS_NOT_FOUND");
    }

    @Disabled("IDOR: cancel should verify process belongs to authenticated user — requires Bearer token auth (future work)")
    @Test
    void cancelProcess_shouldRequireOwnership() {
        // When auth is implemented:
        // user1 creates process, user2 (authenticated) tries to cancel → 403 FORBIDDEN
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
}
