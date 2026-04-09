package pl.kathelan.soap.client.simulator;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
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
        seedCapabilities(userId, active, "ACTIVE", authMethods, defaultActiveDevice());
    }

    public void seedCapabilities(String userId, boolean active, List<String> authMethods, List<Map<String, Object>> devices) {
        seedCapabilities(userId, active, "ACTIVE", authMethods, devices);
    }

    public void seedCapabilities(String userId, boolean active, String accountStatus, List<String> authMethods, List<Map<String, Object>> devices) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("active", active);
        body.put("accountStatus", accountStatus);
        body.put("authMethods", authMethods);
        body.put("devices", devices);
        restClient.post()
                .uri("/simulator/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public static List<Map<String, Object>> defaultActiveDevice() {
        Map<String, Object> device = new HashMap<>();
        device.put("deviceId", "sim-device-001");
        device.put("status", "ACTIVE");
        device.put("isMainDevice", true);
        device.put("isPassiveMode", false);
        device.put("deviceType", "MOBILE");
        return List.of(device);
    }

    public void reset() {
        restClient.delete()
                .uri("/simulator/reset")
                .retrieve()
                .toBodilessEntity();
    }
}
