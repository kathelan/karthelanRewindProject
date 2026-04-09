package pl.kathelan.auth.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.auth.api.dto.AccountStatus;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.api.dto.DeviceStatus;
import pl.kathelan.auth.pipeline.DeviceProcessingContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationDateFilterTest {

    private ActivationDateFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ActivationDateFilter();
    }

    @Test
    void processKeepsDevicesActivatedMoreThan30DaysAgo() {
        LocalDateTime oldDate = LocalDateTime.now().minusDays(31);
        List<DeviceDto> devices = List.of(device("d1", oldDate));

        List<DeviceDto> result = filter.process(devices, ctx());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceId()).isEqualTo("d1");
    }

    @Test
    void processFiltersOutDevicesActivatedLessThan30DaysAgo() {
        LocalDateTime recentDate = LocalDateTime.now().minusDays(10);
        List<DeviceDto> devices = List.of(device("d1", recentDate));

        List<DeviceDto> result = filter.process(devices, ctx());

        assertThat(result).isEmpty();
    }

    @Test
    void processKeepsDevicesWithNullActivationDate() {
        List<DeviceDto> devices = List.of(device("d1", null));

        List<DeviceDto> result = filter.process(devices, ctx());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceId()).isEqualTo("d1");
    }

    @Test
    void processMixedDatesKeepsOnlyOldAndNull() {
        LocalDateTime old = LocalDateTime.now().minusDays(60);
        LocalDateTime recent = LocalDateTime.now().minusDays(5);

        List<DeviceDto> devices = List.of(
                device("d1", old),
                device("d2", recent),
                device("d3", null)
        );

        List<DeviceDto> result = filter.process(devices, ctx());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DeviceDto::deviceId).containsExactlyInAnyOrder("d1", "d3");
    }

    @Test
    void processReturnsEmptyWhenInputEmpty() {
        List<DeviceDto> result = filter.process(List.of(), ctx());

        assertThat(result).isEmpty();
    }

    @Test
    void processFiltersBorderlineDevice_justUnder30Days() {
        // activationDate 29 days ago — NOT before now().minusDays(30), so filtered out
        LocalDateTime justUnder30Days = LocalDateTime.now().minusDays(29);
        List<DeviceDto> devices = List.of(device("d1", justUnder30Days));

        List<DeviceDto> result = filter.process(devices, ctx());

        assertThat(result).isEmpty();
    }

    private DeviceDto device(String deviceId, LocalDateTime activationDate) {
        return new DeviceDto(deviceId, DeviceStatus.ACTIVE, false, false, activationDate, "MOBILE");
    }

    private DeviceProcessingContext ctx() {
        return new DeviceProcessingContext("user1", AccountStatus.ACTIVE);
    }
}
