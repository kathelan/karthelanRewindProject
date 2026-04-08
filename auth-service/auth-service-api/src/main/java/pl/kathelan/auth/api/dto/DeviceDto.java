package pl.kathelan.auth.api.dto;

import java.time.LocalDateTime;

public record DeviceDto(
        String deviceId,
        DeviceStatus status,
        boolean isMainDevice,
        boolean isPassiveMode,
        LocalDateTime activationDate,
        String deviceType
) {}
