package pl.kathelan.auth.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.auth.api.dto.AccountStatus;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.api.dto.DeviceStatus;
import pl.kathelan.auth.exception.AccountNotEligibleException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountStatusValidationStepTest {

    private AccountStatusValidationStep step;

    @BeforeEach
    void setUp() {
        step = new AccountStatusValidationStep();
    }

    @Test
    void processPassesThroughDevicesWhenAccountActive() {
        List<DeviceDto> devices = List.of(device("d1"), device("d2"));
        DeviceProcessingContext context = new DeviceProcessingContext("user1", AccountStatus.ACTIVE);

        List<DeviceDto> result = step.process(devices, context);

        assertThat(result).isSameAs(devices);
    }

    @Test
    void processPassesThroughDevicesWhenAccountStatusNull() {
        List<DeviceDto> devices = List.of(device("d1"));
        DeviceProcessingContext context = new DeviceProcessingContext("user1", null);

        List<DeviceDto> result = step.process(devices, context);

        assertThat(result).isSameAs(devices);
    }

    @Test
    void processThrowsWhenAccountBlocked() {
        List<DeviceDto> devices = List.of(device("d1"));
        DeviceProcessingContext context = new DeviceProcessingContext("user42", AccountStatus.BLOCKED);

        assertThatThrownBy(() -> step.process(devices, context))
                .isInstanceOf(AccountNotEligibleException.class)
                .hasMessageContaining("user42")
                .hasMessageContaining("BLOCKED");
    }

    @Test
    void processThrowsWhenAccountSuspended() {
        List<DeviceDto> devices = List.of(device("d1"));
        DeviceProcessingContext context = new DeviceProcessingContext("user42", AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> step.process(devices, context))
                .isInstanceOf(AccountNotEligibleException.class)
                .hasMessageContaining("user42")
                .hasMessageContaining("SUSPENDED");
    }

    @Test
    void processThrowsWhenDeviceListEmptyAndAccountBlocked() {
        DeviceProcessingContext context = new DeviceProcessingContext("user42", AccountStatus.BLOCKED);

        assertThatThrownBy(() -> step.process(List.of(), context))
                .isInstanceOf(AccountNotEligibleException.class)
                .hasMessageContaining("user42");
    }

    private DeviceDto device(String deviceId) {
        return new DeviceDto(deviceId, DeviceStatus.ACTIVE, false, false, LocalDateTime.now(), "MOBILE");
    }
}
