package pl.kathelan.soap.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.client.core.WebServiceTemplate;
import pl.kathelan.soap.push.generated.GetPushStatusRequest;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesRequest;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushRequest;
import pl.kathelan.soap.push.generated.SendPushResponse;

@Slf4j
@RequiredArgsConstructor
public class MobilePushClientImpl implements MobilePushClient {

    private final WebServiceTemplate webServiceTemplate;

    @Override
    public GetUserCapabilitiesResponse getUserCapabilities(String userId) {
        log.debug("getUserCapabilities: userId={}", userId);
        GetUserCapabilitiesRequest request = new GetUserCapabilitiesRequest();
        request.setUserId(userId);
        GetUserCapabilitiesResponse response =
                (GetUserCapabilitiesResponse) webServiceTemplate.marshalSendAndReceive(request);
        log.debug("getUserCapabilities: response received, errorCode={}", response.getErrorCode());
        return response;
    }

    @Override
    public SendPushResponse sendPush(String userId, String processId) {
        log.debug("sendPush: userId={}, processId={}", userId, processId);
        SendPushRequest request = new SendPushRequest();
        request.setUserId(userId);
        request.setProcessId(processId);
        SendPushResponse response = (SendPushResponse) webServiceTemplate.marshalSendAndReceive(request);
        log.debug("sendPush: response received, sendStatus={}", response.getSendStatus());
        return response;
    }

    @Override
    public GetPushStatusResponse getPushStatus(String deliveryId) {
        log.debug("getPushStatus: deliveryId={}", deliveryId);
        GetPushStatusRequest request = new GetPushStatusRequest();
        request.setDeliveryId(deliveryId);
        GetPushStatusResponse response =
                (GetPushStatusResponse) webServiceTemplate.marshalSendAndReceive(request);
        log.debug("getPushStatus: response received, pushStatus={}", response.getPushStatus());
        return response;
    }
}