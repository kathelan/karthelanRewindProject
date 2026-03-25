package pl.kathelan.soap.push.mapper;

import org.springframework.stereotype.Component;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.domain.SendStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;

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

    public pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse toResponse(UserCapabilities capabilities) {
        pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse response =
                new pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse();
        response.setUserId(capabilities.getUserId());
        response.setActive(capabilities.isActive());
        capabilities.getAuthMethods().stream()
                .map(this::toGenerated)
                .forEach(response.getAuthMethods()::add);
        return response;
    }
}