package pl.kathelan.soap.push.simulator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.kathelan.soap.push.domain.AccountStatus;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.DeviceInfo;
import pl.kathelan.soap.push.domain.DeviceStatus;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;
import pl.kathelan.soap.push.exception.DeliveryNotFoundException;
import pl.kathelan.soap.push.repository.CapabilitiesRepository;
import pl.kathelan.soap.push.repository.InMemoryPushRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/simulator")
@Profile("simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final InMemoryPushRepository pushRepository;
    private final CapabilitiesRepository capabilitiesRepository;

    @PatchMapping("/push/{deliveryId}/status")
    public ResponseEntity<Void> setPushStatus(
            @PathVariable String deliveryId,
            @RequestBody SetPushStatusRequest request) {
        log.info("simulator: setPushStatus deliveryId={}, status={}", deliveryId, request.status());
        try {
            pushRepository.updateStatus(deliveryId, request.status());
            return ResponseEntity.noContent().build();
        } catch (DeliveryNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/capabilities")
    public ResponseEntity<Void> seedCapabilities(@RequestBody SeedCapabilitiesRequest request) {
        log.info("simulator: seedCapabilities userId={}", request.userId());

        List<DeviceInfo> devices = request.devices() == null ? List.of() :
                request.devices().stream()
                        .map(d -> DeviceInfo.builder()
                                .deviceId(d.deviceId())
                                .status(DeviceStatus.valueOf(d.status()))
                                .isMainDevice(d.isMainDevice())
                                .isPassiveMode(d.isPassiveMode())
                                .activationDate(d.activationDate() != null
                                        ? LocalDateTime.parse(d.activationDate())
                                        : null)
                                .deviceType(d.deviceType())
                                .build())
                        .toList();

        capabilitiesRepository.save(UserCapabilities.builder()
                .userId(request.userId())
                .active(request.active())
                .accountStatus(request.accountStatus() != null
                        ? AccountStatus.valueOf(request.accountStatus())
                        : null)
                .authMethods(request.authMethods().stream()
                        .map(AuthMethod::valueOf)
                        .toList())
                .devices(devices)
                .build());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/reset")
    public ResponseEntity<Void> reset() {
        log.info("simulator: reset");
        pushRepository.clear();
        return ResponseEntity.noContent().build();
    }

    record SetPushStatusRequest(PushStatus status) {}

    record SeedCapabilitiesRequest(
            String userId,
            boolean active,
            String accountStatus,
            List<String> authMethods,
            List<DeviceRequest> devices
    ) {}

    record DeviceRequest(
            String deviceId,
            String status,
            boolean isMainDevice,
            boolean isPassiveMode,
            String activationDate,
            String deviceType
    ) {}
}
