package pl.kathelan.soap.client.simulator;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Test helper: HTTP client for the SimulatorController REST API.
 * Use in integration/contract tests to control soap-service behavior
 * without mocking at the Java layer.
 */
public class SimulatorClient {

    private final RestClient restClient;

    public SimulatorClient(String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void setPushStatus(String deliveryId, String status) {
        restClient.patch()
                .uri("/simulator/push/{deliveryId}/status", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status))
                .retrieve()
                .toBodilessEntity();
    }

    public void seedCapabilities(String userId, boolean active, List<String> authMethods) {
        restClient.post()
                .uri("/simulator/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("userId", userId, "active", active, "authMethods", authMethods))
                .retrieve()
                .toBodilessEntity();
    }

    public void reset() {
        restClient.delete()
                .uri("/simulator/reset")
                .retrieve()
                .toBodilessEntity();
    }
}
