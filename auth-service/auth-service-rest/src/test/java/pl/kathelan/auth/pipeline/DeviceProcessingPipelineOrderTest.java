package pl.kathelan.auth.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.auth.api.dto.AccountStatus;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.api.dto.DeviceStatus;
import pl.kathelan.auth.exception.AccountNotEligibleException;
import pl.kathelan.auth.exception.AllDevicesBlockedException;
import pl.kathelan.auth.exception.NoDevicesFoundException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that pipeline steps fire in correct order:
 * 0. AccountStatusValidationStep  — blocks BLOCKED/SUSPENDED accounts immediately
 * 1. ActiveDeviceValidationStep   — keeps only ACTIVE devices, throws on none
 * 2. PassiveModeFilter            — removes passive devices
 * 3. ActivationDateFilter         — removes devices activated < 30 days ago
 */
class DeviceProcessingPipelineOrderTest {

    private DeviceProcessingPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new DeviceProcessingPipeline(List.of(
                new AccountStatusValidationStep(),
                new ActiveDeviceValidationStep(),
                new PassiveModeFilter(),
                new ActivationDateFilter()
        ));
    }

    @Test
    void accountStatusStep0_firesBeforeDeviceCheck_blockedAccountWithNoDevicesThrowsAccountNotEligible() {
        // BLOCKED account, no devices — step 0 must fire first (not step 1)
        DeviceProcessingContext ctx = new DeviceProcessingContext("user42", AccountStatus.BLOCKED);

        assertThatThrownBy(() -> pipeline.execute(List.of(), ctx))
                .isInstanceOf(AccountNotEligibleException.class)
                .hasMessageContaining("user42")
                .hasMessageContaining("BLOCKED");
    }

    @Test
    void accountStatusStep0_firesBeforeDeviceCheck_suspendedAccountWithActiveDevicesThrowsAccountNotEligible() {
        // SUSPENDED account with ACTIVE device — step 0 must fire, device is irrelevant
        DeviceProcessingContext ctx = new DeviceProcessingContext("user42", AccountStatus.SUSPENDED);
        List<DeviceDto> devices = List.of(activeDevice("d1"));

        assertThatThrownBy(() -> pipeline.execute(devices, ctx))
                .isInstanceOf(AccountNotEligibleException.class)
                .hasMessageContaining("SUSPENDED");
    }

    @Test
    void step1_activeDeviceValidation_firesAfterAccountCheck_allBlockedDevicesThrowsAllDevicesBlocked() {
        // ACTIVE account, all devices BLOCKED — step 0 passes, step 1 throws AllDevicesBlocked
        DeviceProcessingContext ctx = new DeviceProcessingContext("user42", AccountStatus.ACTIVE);
        List<DeviceDto> devices = List.of(blockedDevice("d1"), blockedDevice("d2"));

        assertThatThrownBy(() -> pipeline.execute(devices, ctx))
                .isInstanceOf(AllDevicesBlockedException.class)
                .hasMessageContaining("user42");
    }

    @Test
    void step2_passiveModeFilter_removesPassiveAfterActiveFilter() {
        // 2 active devices: one passive, one not — result: only non-passive survives
        DeviceProcessingContext ctx = new DeviceProcessingContext("user1", AccountStatus.ACTIVE);
        List<DeviceDto> devices = List.of(
                activeOldDevice("d1", false),
                activeOldDevice("d2", true) // passive
        );

        List<DeviceDto> result = pipeline.execute(devices, ctx);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceId()).isEqualTo("d1");
    }

    @Test
    void step3_activationDateFilter_removesRecentlyActivatedDevices() {
        // 2 active non-passive devices: one old (>30d), one recent (<30d) — result: only old survives
        DeviceProcessingContext ctx = new DeviceProcessingContext("user1", AccountStatus.ACTIVE);
        List<DeviceDto> devices = List.of(
                deviceActivatedDaysAgo("d1", 60),
                deviceActivatedDaysAgo("d2", 10)
        );

        List<DeviceDto> result = pipeline.execute(devices, ctx);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceId()).isEqualTo("d1");
    }

    @Test
    void fullPipeline_activeAccountOldActiveNonPassiveDevice_passes() {
        DeviceProcessingContext ctx = new DeviceProcessingContext("user1", AccountStatus.ACTIVE);
        List<DeviceDto> devices = List.of(activeOldDevice("d1", false));

        List<DeviceDto> result = pipeline.execute(devices, ctx);

        assertThat(result).hasSize(1);
    }

    @Test
    void noDevicesAfterAllFilters_step1ThrowsNoDevicesFound_whenAllInactive() {
        // ACTIVE account, only INACTIVE devices — step 1 throws NoDevicesFound (not AccountNotEligible)
        DeviceProcessingContext ctx = new DeviceProcessingContext("user42", AccountStatus.ACTIVE);
        List<DeviceDto> devices = List.of(
                new DeviceDto("d1", DeviceStatus.INACTIVE, false, false, LocalDateTime.now().minusDays(60), "MOBILE")
        );

        assertThatThrownBy(() -> pipeline.execute(devices, ctx))
                .isInstanceOf(NoDevicesFoundException.class)
                .hasMessageContaining("user42");
    }

    // --- helpers ---

    private DeviceDto activeDevice(String id) {
        return new DeviceDto(id, DeviceStatus.ACTIVE, false, false, LocalDateTime.now().minusDays(60), "MOBILE");
    }

    private DeviceDto blockedDevice(String id) {
        return new DeviceDto(id, DeviceStatus.BLOCKED, false, false, LocalDateTime.now().minusDays(60), "MOBILE");
    }

    private DeviceDto activeOldDevice(String id, boolean isPassive) {
        return new DeviceDto(id, DeviceStatus.ACTIVE, false, isPassive, LocalDateTime.now().minusDays(60), "MOBILE");
    }

    private DeviceDto deviceActivatedDaysAgo(String id, int daysAgo) {
        return new DeviceDto(id, DeviceStatus.ACTIVE, false, false, LocalDateTime.now().minusDays(daysAgo), "MOBILE");
    }
}
