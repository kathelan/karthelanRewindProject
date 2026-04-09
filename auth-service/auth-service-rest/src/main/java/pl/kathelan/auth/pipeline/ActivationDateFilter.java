package pl.kathelan.auth.pipeline;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.kathelan.auth.api.dto.DeviceDto;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(3)
public class ActivationDateFilter implements DeviceProcessingStep {

    @Override
    public List<DeviceDto> process(List<DeviceDto> devices, DeviceProcessingContext context) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        return devices.stream()
                .filter(d -> d.activationDate() == null || d.activationDate().isBefore(threshold))
                .toList();
    }
}
