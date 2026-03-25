package pl.kathelan.soap.client;

import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushResponse;

public interface MobilePushClient {

    GetUserCapabilitiesResponse getUserCapabilities(String userId);

    SendPushResponse sendPush(String userId, String processId);

    GetPushStatusResponse getPushStatus(String deliveryId);
}