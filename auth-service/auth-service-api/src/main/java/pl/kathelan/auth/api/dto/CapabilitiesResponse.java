package pl.kathelan.auth.api.dto;

import java.util.List;

public record CapabilitiesResponse(
        String userId,
        boolean active,
        AccountStatus accountStatus,
        List<AuthMethod> authMethods,
        List<DeviceDto> devices
) {}