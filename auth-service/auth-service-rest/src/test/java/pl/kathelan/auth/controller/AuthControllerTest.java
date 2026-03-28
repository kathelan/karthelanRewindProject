package pl.kathelan.auth.controller;

import org.junit.jupiter.api.Test;
import pl.kathelan.auth.AuthServiceBaseTest;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.InitProcessRequest;

import java.util.UUID;

class AuthControllerTest extends AuthServiceBaseTest {

    // --- GET /auth/capabilities/{userId} ---

    @Test
    void getCapabilities_returns200WithCapabilities() throws Exception {
        stubGetCapabilities("user1", true, pl.kathelan.soap.push.generated.AuthMethod.PUSH);

        String expected = new String(getClass().getResourceAsStream("/expected/capabilities_response.json").readAllBytes());

        doGet("/auth/capabilities/{userId}", "user1")
                .expectStatus().isOk()
                .expectBody()
                .json(expected);
    }

    // --- POST /process/init ---

    @Test
    void init_returns201WithProcessIdAndExpiresAt() {
        stubSendPush("delivery-1");

        doPost("/process/init", new InitProcessRequest("user1", AuthMethod.PUSH))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.processId").isNotEmpty()
                .jsonPath("$.data.expiresAt").isNotEmpty();
    }

    @Test
    void init_returns400WhenBodyInvalid() {
        doPost("/process/init", new InitProcessRequest("", null))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void init_closesPreviousPendingProcessForSameUser() throws Exception {
        stubSendPush("delivery-1");
        String firstProcessId = initProcess("user1");

        stubSendPush("delivery-2");
        String secondProcessId = initProcess("user1");

        doGetSse("/process/{id}/stream", secondProcessId)
                .expectStatus().isOk();
        doGetSse("/process/{id}/stream", firstProcessId)
                .expectStatus().isOk();
    }

    // --- GET /process/{id}/stream ---

    @Test
    void stream_returns200ForPendingProcess() throws Exception {
        stubSendPush("delivery-1");
        String processId = initProcess("user1");

        doGetSse("/process/{id}/stream", processId)
                .expectStatus().isOk();
    }

    @Test
    void stream_returns404WhenProcessNotFound() {
        doGet("/process/{id}/stream", UUID.randomUUID())
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("PROCESS_NOT_FOUND");
    }

    // --- PATCH /process/{id}/cancel ---

    @Test
    void cancel_returns204ForPendingProcess() throws Exception {
        stubSendPush("delivery-1");
        String processId = initProcess("user1");

        doPatch("/process/{id}/cancel", processId)
                .expectStatus().isNoContent();
    }

    @Test
    void cancel_returns404WhenProcessNotFound() {
        doPatch("/process/{id}/cancel", UUID.randomUUID())
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("PROCESS_NOT_FOUND");
    }

    @Test
    void cancel_returns409WhenAlreadyCancelled() throws Exception {
        stubSendPush("delivery-1");
        String processId = initProcess("user1");
        doPatch("/process/{id}/cancel", processId);

        doPatch("/process/{id}/cancel", processId)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("INVALID_STATE_TRANSITION");
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