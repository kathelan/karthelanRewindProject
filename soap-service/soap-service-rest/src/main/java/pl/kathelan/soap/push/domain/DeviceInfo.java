package pl.kathelan.soap.push.domain;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class DeviceInfo {
    String deviceId;
    DeviceStatus status;
    boolean isMainDevice;
    boolean isPassiveMode;
    LocalDateTime activationDate;
    String deviceType;
}
