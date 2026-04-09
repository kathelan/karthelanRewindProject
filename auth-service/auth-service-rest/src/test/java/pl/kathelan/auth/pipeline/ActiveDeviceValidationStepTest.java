package pl.kathelan.auth.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.auth.api.dto.AccountStatus;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.api.dto.DeviceStatus;
import pl.kathelan.auth.exception.AllDevicesBlockedException;
import pl.kathelan.auth.exception.NoDevicesFoundException;
import pl.kathelan.auth.pipeline.DeviceProcessingContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveDeviceValidationStepTest {

    private ActiveDeviceValidationStep step;

    @BeforeEach
    void setUp() {
        step = new ActiveDeviceValidationStep();
    }

    @Test
    void processReturnOnlyActiveDevicesWhenMixed() {
        List<DeviceDto> devices = List.of(
                device("d1", DeviceStatus.ACTIVE),
                device("d2", DeviceStatus.BLOCKED),
                device("d3", DeviceStatus.INACTIVE),
                device("d4", DeviceStatus.ACTIVE)
        );

        List<DeviceDto> result = step.process(devices, ctx("user1"));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DeviceDto::deviceId).containsExactlyInAnyOrder("d1", "d4");
    }

    @Test
    void processThrowsAllDevicesBlockedExceptionWhenAllBlocked() {
        List<DeviceDto> devices = List.of(
                device("d1", DeviceStatus.BLOCKED),
                device("d2", DeviceStatus.BLOCKED)
        );

        assertThatThrownBy(() -> step.process(devices, ctx("user42")))
                .isInstanceOf(AllDevicesBlockedException.class)
                .hasMessageContaining("user42");
    }

    @Test
    void processThrowsNoDevicesFoundExceptionWhenEmptyList() {
        assertThatThrownBy(() -> step.process(List.of(), ctx("user42")))
                .isInstanceOf(NoDevicesFoundException.class)
                .hasMessageContaining("user42");
    }

    @Test
    void processThrowsNoDevicesFoundExceptionWhenOnlyInactive() {
        List<DeviceDto> devices = List.of(
                device("d1", DeviceStatus.INACTIVE),
                device("d2", DeviceStatus.INACTIVE)
        );

        assertThatThrownBy(() -> step.process(devices, ctx("user42")))
                .isInstanceOf(NoDevicesFoundException.class)
                .hasMessageContaining("user42");
    }

    @Test
    void processReturnsSingleActiveDevice() {
        List<DeviceDto> devices = List.of(
                device("d1", DeviceStatus.ACTIVE),
                device("d2", DeviceStatus.INACTIVE)
        );

        List<DeviceDto> result = step.process(devices, ctx("user1"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceId()).isEqualTo("d1");
    }

    private DeviceDto device(String deviceId, DeviceStatus status) {
        return new DeviceDto(deviceId, status, false, false, LocalDateTime.now(), "MOBILE");
    }

    private DeviceProcessingContext ctx(String userId) {
        return new DeviceProcessingContext(userId, AccountStatus.ACTIVE);
    }
}
