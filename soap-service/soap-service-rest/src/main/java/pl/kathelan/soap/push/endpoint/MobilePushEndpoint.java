package pl.kathelan.soap.push.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import pl.kathelan.soap.push.exception.DeliveryNotFoundException;
import pl.kathelan.soap.push.exception.UserInactiveException;
import pl.kathelan.soap.push.exception.UserNotFoundException;
import pl.kathelan.soap.push.generated.ErrorCode;
import pl.kathelan.soap.push.generated.GetPushStatusRequest;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesRequest;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushRequest;
import pl.kathelan.soap.push.generated.SendPushResponse;
import pl.kathelan.soap.push.service.PushService;

@Slf4j
@Endpoint
@RequiredArgsConstructor
public class MobilePushEndpoint {

    private static final String NAMESPACE = "http://kathelan.pl/soap/push";

    private final PushService pushService;

    @PayloadRoot(namespace = NAMESPACE, localPart = "getUserCapabilitiesRequest")
    @ResponsePayload
    public GetUserCapabilitiesResponse getUserCapabilities(@RequestPayload GetUserCapabilitiesRequest request) {
        return pushService.getUserCapabilities(request.getUserId());
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "sendPushRequest")
    @ResponsePayload
    public SendPushResponse sendPush(@RequestPayload SendPushRequest request) {
        try {
            return pushService.sendPush(request.getUserId(), request.getProcessId());
        } catch (UserNotFoundException e) {
            log.warn("sendPush: user not found, userId={}", request.getUserId());
            SendPushResponse response = new SendPushResponse();
            response.setErrorCode(ErrorCode.USER_NOT_FOUND);
            response.setMessage(e.getMessage());
            return response;
        } catch (UserInactiveException e) {
            log.warn("sendPush: user inactive, userId={}", request.getUserId());
            SendPushResponse response = new SendPushResponse();
            response.setErrorCode(ErrorCode.USER_INACTIVE);
            response.setMessage(e.getMessage());
            return response;
        }
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "getPushStatusRequest")
    @ResponsePayload
    public GetPushStatusResponse getPushStatus(@RequestPayload GetPushStatusRequest request) {
        try {
            return pushService.getPushStatus(request.getDeliveryId());
        } catch (DeliveryNotFoundException e) {
            log.warn("getPushStatus: delivery not found, deliveryId={}", request.getDeliveryId());
            GetPushStatusResponse response = new GetPushStatusResponse();
            response.setErrorCode(ErrorCode.DELIVERY_NOT_FOUND);
            response.setMessage(e.getMessage());
            return response;
        }
    }
}
