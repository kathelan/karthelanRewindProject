package pl.kathelan.soap.push.mapper;

import org.springframework.stereotype.Component;
import pl.kathelan.soap.push.domain.AccountStatus;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.DeviceInfo;
import pl.kathelan.soap.push.domain.DeviceStatus;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.domain.SendStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.GregorianCalendar;
import java.util.Optional;

@Component
public class MobilePushMapper {

    public pl.kathelan.soap.push.generated.AuthMethod toGenerated(AuthMethod method) {
        return pl.kathelan.soap.push.generated.AuthMethod.fromValue(method.name());
    }

    public pl.kathelan.soap.push.generated.PushStatus toGenerated(PushStatus status) {
        return pl.kathelan.soap.push.generated.PushStatus.fromValue(status.name());
    }

    public pl.kathelan.soap.push.generated.SendStatus toGenerated(SendStatus status) {
        return pl.kathelan.soap.push.generated.SendStatus.fromValue(status.name());
    }

    public pl.kathelan.soap.push.generated.AccountStatus toGenerated(AccountStatus status) {
        return pl.kathelan.soap.push.generated.AccountStatus.fromValue(status.name());
    }

    public pl.kathelan.soap.push.generated.DeviceStatus toGenerated(DeviceStatus status) {
        return pl.kathelan.soap.push.generated.DeviceStatus.fromValue(status.name());
    }

    public pl.kathelan.soap.push.generated.DeviceDto toGenerated(DeviceInfo device) {
        pl.kathelan.soap.push.generated.DeviceDto dto = new pl.kathelan.soap.push.generated.DeviceDto();
        dto.setDeviceId(device.getDeviceId());
        dto.setStatus(toGenerated(device.getStatus()));
        dto.setIsMainDevice(device.isMainDevice());
        dto.setIsPassiveMode(device.isPassiveMode());
        Optional.ofNullable(device.getActivationDate())
                .map(this::toXmlDateTime)
                .ifPresent(dto::setActivationDate);
        Optional.ofNullable(device.getDeviceType())
                .ifPresent(dto::setDeviceType);
        return dto;
    }

    public pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse toResponse(UserCapabilities capabilities) {
        pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse response =
                new pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse();
        response.setUserId(capabilities.getUserId());
        response.setActive(capabilities.isActive());
        capabilities.getAuthMethods().stream()
                .map(this::toGenerated)
                .forEach(response.getAuthMethods()::add);
        Optional.ofNullable(capabilities.getAccountStatus())
                .map(this::toGenerated)
                .ifPresent(response::setAccountStatus);
        Optional.ofNullable(capabilities.getDevices())
                .ifPresent(devices -> devices.stream()
                        .map(this::toGenerated)
                        .forEach(response.getDevices()::add));
        return response;
    }

    public XMLGregorianCalendar toXmlDateTime(LocalDateTime dt) {
        try {
            GregorianCalendar gc = GregorianCalendar.from(dt.atZone(ZoneId.systemDefault()));
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("Cannot create XMLGregorianCalendar", e);
        }
    }
}
