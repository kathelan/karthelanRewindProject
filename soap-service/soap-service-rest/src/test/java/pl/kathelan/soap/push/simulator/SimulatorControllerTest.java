package pl.kathelan.soap.push.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.kathelan.soap.push.domain.PushRecord;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.repository.InMemoryCapabilitiesRepository;
import pl.kathelan.soap.push.repository.InMemoryPushRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SimulatorControllerTest — integration tests for the REST simulator used in e2e test scenarios.
 *
 * <p>The {@code SimulatorController} (active under profile {@code simulator}) exposes REST endpoints
 * that allow tests to:
 * <ul>
 *   <li>update push delivery statuses ({@code PATCH /simulator/push/{deliveryId}/status})</li>
 *   <li>seed user capabilities ({@code POST /simulator/capabilities})</li>
 *   <li>reset all push records ({@code DELETE /simulator/reset})</li>
 * </ul>
 *
 * <p>These operations replace manual data setup in end-to-end flow tests and are never
 * deployed to production — only the {@code simulator} Spring profile activates them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "simulator"})
@TestPropertySource(properties = "soap.security.users.admin=adminpass")
@DisplayName("SimulatorController — REST control plane for e2e push testing")
class SimulatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InMemoryPushRepository pushRepository;

    @Autowired
    private InMemoryCapabilitiesRepository capabilitiesRepository;

    @BeforeEach
    void reset() {
        pushRepository.clear();
    }

    // ===== PATCH /simulator/push/{deliveryId}/status =====

    @Nested
    @DisplayName("PATCH /simulator/push/{deliveryId}/status — updating push delivery status")
    class SetPushStatus {

        /**
         * Successful status update: patching an existing delivery with a new status must
         * return HTTP 204 (no content) and the in-memory store must reflect the new status.
         * This is the mechanism auth-service uses to simulate a user approving a push.
         */
        @Test
        @DisplayName("returns 204 and persists the new status for an existing delivery")
        void setPushStatus_returns204AndUpdatesStatus() throws Exception {
            pushRepository.save(PushRecord.builder()
                    .deliveryId("delivery-sim-1")
                    .userId("user-push")
                    .processId("proc-1")
                    .status(PushStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(2))
                    .build());

            mockMvc.perform(patch("/simulator/push/delivery-sim-1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                    .andExpect(status().isNoContent());

            assertThat(pushRepository.findByDeliveryId("delivery-sim-1"))
                    .hasValueSatisfying(r -> assertThat(r.getStatus()).isEqualTo(PushStatus.APPROVED));
        }

        /**
         * Unknown delivery: patching a deliveryId that does not exist in the repository
         * must return HTTP 404. Silently ignoring unknown ids would hide e2e test setup bugs.
         */
        @Test
        @DisplayName("returns 404 when the deliveryId does not exist")
        void setPushStatus_returns404WhenDeliveryNotFound() throws Exception {
            mockMvc.perform(patch("/simulator/push/nonexistent/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                    .andExpect(status().isNotFound());
        }
    }

    // ===== POST /simulator/capabilities =====

    @Nested
    @DisplayName("POST /simulator/capabilities — seeding user capabilities for e2e tests")
    class SeedCapabilities {

        /**
         * Capabilities seeding: posting a capabilities payload must return HTTP 204
         * and the capabilities must be immediately retrievable from the repository.
         * This allows e2e tests to create users with specific auth methods on the fly.
         */
        @Test
        @DisplayName("returns 204 and makes the seeded capabilities available by userId")
        void seedCapabilities_returns204AndCapabilitiesAvailable() throws Exception {
            mockMvc.perform(post("/simulator/capabilities")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "userId", "user-e2e",
                                    "active", true,
                                    "authMethods", List.of("PUSH")
                            ))))
                    .andExpect(status().isNoContent());

            assertThat(capabilitiesRepository.findByUserId("user-e2e"))
                    .hasValueSatisfying(c -> {
                        assertThat(c.isActive()).isTrue();
                        assertThat(c.getUserId()).isEqualTo("user-e2e");
                    });
        }
    }

    // ===== DELETE /simulator/reset =====

    @Nested
    @DisplayName("DELETE /simulator/reset — clearing all push records between test runs")
    class Reset {

        /**
         * Reset: calling DELETE /simulator/reset must return HTTP 204 and remove all push records
         * from the repository so subsequent tests start with a clean state.
         * Without reset, stateful in-memory data would leak between test scenarios.
         */
        @Test
        @DisplayName("returns 204 and removes all push records from the repository")
        void reset_returns204AndClearsPushRecords() throws Exception {
            pushRepository.save(PushRecord.builder()
                    .deliveryId("delivery-to-clear")
                    .userId("user-push")
                    .processId("proc-1")
                    .status(PushStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(2))
                    .build());

            mockMvc.perform(delete("/simulator/reset"))
                    .andExpect(status().isNoContent());

            assertThat(pushRepository.findByDeliveryId("delivery-to-clear")).isEmpty();
        }
    }
}
