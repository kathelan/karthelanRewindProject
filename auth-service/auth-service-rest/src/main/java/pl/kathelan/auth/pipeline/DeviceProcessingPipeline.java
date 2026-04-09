package pl.kathelan.auth.pipeline;

import org.springframework.stereotype.Component;
import pl.kathelan.auth.api.dto.DeviceDto;

import java.util.List;

@Component
public class DeviceProcessingPipeline {

    private final List<DeviceProcessingStep> steps;

    public DeviceProcessingPipeline(List<DeviceProcessingStep> steps) {
        this.steps = steps;
    }

    public List<DeviceDto> execute(List<DeviceDto> devices, DeviceProcessingContext context) {
        var result = devices;
        for (var step : steps) {
            result = step.process(result, context);
        }
        return result;
    }
}
