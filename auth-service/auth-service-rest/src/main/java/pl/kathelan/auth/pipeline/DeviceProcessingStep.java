package pl.kathelan.auth.pipeline;

import pl.kathelan.auth.api.dto.DeviceDto;

import java.util.List;

public interface DeviceProcessingStep {
    List<DeviceDto> process(List<DeviceDto> devices, DeviceProcessingContext context);
}
