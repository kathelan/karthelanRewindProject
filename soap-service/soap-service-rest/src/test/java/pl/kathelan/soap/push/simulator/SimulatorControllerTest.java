package pl.kathelan.soap.push.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "simulator"})
@TestPropertySource(properties = "soap.security.users.admin=adminpass")
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

    @Test
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

    @Test
    void setPushStatus_returns404WhenDeliveryNotFound() throws Exception {
        mockMvc.perform(patch("/simulator/push/nonexistent/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
                .andExpect(status().isNotFound());
    }

    @Test
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

    @Test
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
