package pl.kathelan.auth.mapper;

import org.springframework.stereotype.Component;
import pl.kathelan.auth.api.dto.AccountStatus;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.CapabilitiesResponse;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.api.dto.DeviceStatus;
import pl.kathelan.common.util.XmlDateTimeUtils;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;

import java.util.List;
import java.util.Optional;

@Component
public class CapabilitiesMapper {

    public CapabilitiesResponse toCapabilitiesResponse(GetUserCapabilitiesResponse soap) {
        List<AuthMethod> methods = soap.getAuthMethods().stream()
                .map(m -> AuthMethod.valueOf(m.name()))
                .toList();

        AccountStatus accountStatus = Optional.ofNullable(soap.getAccountStatus())
                .map(s -> AccountStatus.valueOf(s.value()))
                .orElse(null);

        List<DeviceDto> devices = soap.getDevices().stream()
                .map(this::toDeviceDto)
                .toList();

        return new CapabilitiesResponse(soap.getUserId(), soap.isActive(), accountStatus, methods, devices);
    }

    private DeviceDto toDeviceDto(pl.kathelan.soap.push.generated.DeviceDto d) {
        return new DeviceDto(
                d.getDeviceId(),
                DeviceStatus.valueOf(d.getStatus().value()),
                d.isIsMainDevice(),
                d.isIsPassiveMode(),
                Optional.ofNullable(d.getActivationDate())
                        .map(XmlDateTimeUtils::toLocalDateTime)
                        .orElse(null),
                d.getDeviceType()
        );
    }
}
