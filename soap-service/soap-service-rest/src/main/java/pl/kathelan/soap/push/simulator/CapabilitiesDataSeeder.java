package pl.kathelan.soap.push.simulator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.kathelan.soap.push.domain.AccountStatus;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.DeviceInfo;
import pl.kathelan.soap.push.domain.DeviceStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;
import pl.kathelan.soap.push.repository.CapabilitiesRepository;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("simulator")
@RequiredArgsConstructor
public class CapabilitiesDataSeeder {

    private final CapabilitiesRepository capabilitiesRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        log.info("simulator: seeding capabilities data");

        capabilitiesRepository.save(UserCapabilities.builder()
                .userId("user-push")
                .active(true)
                .accountStatus(AccountStatus.ACTIVE)
                .authMethods(List.of(AuthMethod.PUSH))
                .devices(List.of(
                        DeviceInfo.builder()
                                .deviceId("device-001")
                                .status(DeviceStatus.ACTIVE)
                                .isMainDevice(true)
                                .isPassiveMode(false)
                                .activationDate(LocalDateTime.now().minusDays(60))
                                .deviceType("MOBILE")
                                .build(),
                        DeviceInfo.builder()
                                .deviceId("device-002")
                                .status(DeviceStatus.ACTIVE)
                                .isMainDevice(false)
                                .isPassiveMode(false)
                                .activationDate(LocalDateTime.now().minusDays(10))
                                .deviceType("TABLET")
                                .build()
                ))
                .build());

        capabilitiesRepository.save(UserCapabilities.builder()
                .userId("user-multi")
                .active(true)
                .accountStatus(AccountStatus.ACTIVE)
                .authMethods(List.of(AuthMethod.PUSH, AuthMethod.SMS))
                .devices(List.of())
                .build());

        capabilitiesRepository.save(UserCapabilities.builder()
                .userId("user-inactive")
                .active(false)
                .accountStatus(AccountStatus.SUSPENDED)
                .authMethods(List.of(AuthMethod.PUSH))
                .devices(List.of())
                .build());

        capabilitiesRepository.save(UserCapabilities.builder()
                .userId("user-blocked-devices")
                .active(true)
                .accountStatus(AccountStatus.ACTIVE)
                .authMethods(List.of(AuthMethod.PUSH))
                .devices(List.of(
                        DeviceInfo.builder()
                                .deviceId("device-blk-001")
                                .status(DeviceStatus.BLOCKED)
                                .isMainDevice(true)
                                .isPassiveMode(false)
                                .activationDate(LocalDateTime.now().minusDays(90))
                                .build()
                ))
                .build());

        log.info("simulator: seeded {} users", 4);
    }
}
