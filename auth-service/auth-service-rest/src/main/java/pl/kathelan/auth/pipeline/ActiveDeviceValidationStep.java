package pl.kathelan.auth.pipeline;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.api.dto.DeviceStatus;
import pl.kathelan.auth.exception.AllDevicesBlockedException;
import pl.kathelan.auth.exception.NoDevicesFoundException;

import java.util.List;

@Component
@Order(1)
public class ActiveDeviceValidationStep implements DeviceProcessingStep {

    @Override
    public List<DeviceDto> process(List<DeviceDto> devices, String userId) {
        List<DeviceDto> filtered = devices.stream()
                .filter(d -> d.status() == DeviceStatus.ACTIVE)
                .toList();

        if (!filtered.isEmpty()) {
            return filtered;
        }

        boolean hasBlocked = devices.stream().anyMatch(d -> d.status() == DeviceStatus.BLOCKED);
        if (hasBlocked) {
            throw new AllDevicesBlockedException(userId);
        }

        throw new NoDevicesFoundException(userId);
    }
}
