package pl.kathelan.auth.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.api.dto.DeviceStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PassiveModeFilterTest {

    private PassiveModeFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PassiveModeFilter();
    }

    @Test
    void processFiltersOutPassiveModeDevices() {
        List<DeviceDto> devices = List.of(
                device("d1", false),
                device("d2", true),
                device("d3", false),
                device("d4", true)
        );

        List<DeviceDto> result = filter.process(devices, "user1");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DeviceDto::deviceId).containsExactlyInAnyOrder("d1", "d3");
    }

    @Test
    void processReturnsEmptyListWhenAllPassive() {
        List<DeviceDto> devices = List.of(
                device("d1", true),
                device("d2", true)
        );

        List<DeviceDto> result = filter.process(devices, "user1");

        assertThat(result).isEmpty();
    }

    @Test
    void processReturnsAllWhenNonePassive() {
        List<DeviceDto> devices = List.of(
                device("d1", false),
                device("d2", false)
        );

        List<DeviceDto> result = filter.process(devices, "user1");

        assertThat(result).hasSize(2);
    }

    @Test
    void processReturnsEmptyWhenInputEmpty() {
        List<DeviceDto> result = filter.process(List.of(), "user1");

        assertThat(result).isEmpty();
    }

    private DeviceDto device(String deviceId, boolean isPassiveMode) {
        return new DeviceDto(deviceId, DeviceStatus.ACTIVE, false, isPassiveMode, LocalDateTime.now(), "MOBILE");
    }
}
