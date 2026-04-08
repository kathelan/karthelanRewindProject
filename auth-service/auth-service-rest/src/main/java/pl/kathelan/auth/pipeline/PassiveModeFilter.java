package pl.kathelan.auth.pipeline;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.kathelan.auth.api.dto.DeviceDto;

import java.util.List;

@Component
@Order(2)
public class PassiveModeFilter implements DeviceProcessingStep {

    @Override
    public List<DeviceDto> process(List<DeviceDto> devices, String userId) {
        return devices.stream()
                .filter(d -> !d.isPassiveMode())
                .toList();
    }
}
