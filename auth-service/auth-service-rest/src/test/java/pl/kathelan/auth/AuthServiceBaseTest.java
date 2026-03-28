package pl.kathelan.auth;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.common.test.BaseIntegrationTest;
import pl.kathelan.common.util.XmlDateTimeUtils;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.push.generated.AuthMethod;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.PushStatus;
import pl.kathelan.soap.push.generated.SendPushResponse;
import pl.kathelan.soap.push.generated.SendStatus;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public abstract class AuthServiceBaseTest extends BaseIntegrationTest {

    @MockitoBean
    protected MobilePushClient mobilePushClient;

    @Autowired
    private InMemoryAuthProcessRepository repository;

    @BeforeEach
    void resetRepository() {
        repository.clear();
    }

    // --- MobilePushClient stubs ---

    protected void stubSendPush(String deliveryId) {
        stubSendPush(deliveryId, LocalDateTime.now().plusMinutes(2));
    }

    protected void stubSendPush(String deliveryId, LocalDateTime expiresAt) {
        SendPushResponse response = new SendPushResponse();
        response.setDeliveryId(deliveryId);
        response.setSendStatus(SendStatus.SENT);
        response.setExpiresAt(XmlDateTimeUtils.toXmlGregorianCalendar(expiresAt));
        when(mobilePushClient.sendPush(anyString(), anyString())).thenReturn(response);
    }

    protected void stubGetPushStatus(String deliveryId, PushStatus status) {
        GetPushStatusResponse response = new GetPushStatusResponse();
        response.setDeliveryId(deliveryId);
        response.setPushStatus(status);
        when(mobilePushClient.getPushStatus(deliveryId)).thenReturn(response);
    }

    protected void stubGetCapabilities(String userId, boolean active, AuthMethod... methods) {
        GetUserCapabilitiesResponse response = new GetUserCapabilitiesResponse();
        response.setUserId(userId);
        response.setActive(active);
        for (AuthMethod method : methods) {
            response.getAuthMethods().add(method);
        }
        when(mobilePushClient.getUserCapabilities(userId)).thenReturn(response);
    }
}