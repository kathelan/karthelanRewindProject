package pl.kathelan.soap.push.service;

import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushResponse;

public interface PushService {

    GetUserCapabilitiesResponse getUserCapabilities(String userId);

    SendPushResponse sendPush(String userId, String processId);

    GetPushStatusResponse getPushStatus(String deliveryId);
}
