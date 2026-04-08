package pl.kathelan.soap.push.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.kathelan.soap.push.domain.AccountStatus;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.DeviceInfo;
import pl.kathelan.soap.push.domain.DeviceStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
@Profile("local")
public class InMemoryCapabilitiesRepository implements CapabilitiesRepository {

    private final Map<String, UserCapabilities> store = new ConcurrentHashMap<>();

    public InMemoryCapabilitiesRepository() {
        seed();
    }

    @Override
    public Optional<UserCapabilities> findByUserId(String userId) {
        log.debug("findByUserId: userId={}", userId);
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public void save(UserCapabilities capabilities) {
        store.put(capabilities.getUserId(), capabilities);
        log.debug("save: userId={}", capabilities.getUserId());
    }

    private void seed() {
        store.put("user-push", UserCapabilities.builder()
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

        store.put("user-multi", UserCapabilities.builder()
                .userId("user-multi")
                .active(true)
                .accountStatus(AccountStatus.ACTIVE)
                .authMethods(List.of(AuthMethod.PUSH, AuthMethod.SMS))
                .devices(List.of())
                .build());

        store.put("user-inactive", UserCapabilities.builder()
                .userId("user-inactive")
                .active(false)
                .accountStatus(AccountStatus.SUSPENDED)
                .authMethods(List.of(AuthMethod.PUSH))
                .devices(List.of())
                .build());

        store.put("user-blocked-devices", UserCapabilities.builder()
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
    }
}
