package pl.kathelan.auth;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.common.test.BaseIntegrationTest;
import pl.kathelan.common.util.XmlDateTimeUtils;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.push.generated.AuthMethod;
import pl.kathelan.soap.push.generated.DeviceDto;
import pl.kathelan.soap.push.generated.DeviceStatus;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.PushStatus;
import pl.kathelan.soap.push.generated.SendPushResponse;
import pl.kathelan.soap.push.generated.SendStatus;

import jakarta.xml.bind.JAXBContext;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

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
        response.getDevices().addAll(defaultActiveDevices());
        when(mobilePushClient.getUserCapabilities(userId)).thenReturn(response);
    }

    // --- XML fixture loader ---

    @SuppressWarnings("unchecked")
    protected <T> T loadFixture(String path, Class<T> clazz) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Fixture not found on classpath: " + path);
            }
            return (T) JAXBContext.newInstance(clazz).createUnmarshaller().unmarshal(stream);
        }
    }

    private List<DeviceDto> defaultActiveDevices() {
        DeviceDto device = new DeviceDto();
        device.setDeviceId("test-device-001");
        device.setStatus(DeviceStatus.ACTIVE);
        device.setIsMainDevice(true);
        device.setIsPassiveMode(false);
        device.setActivationDate(XmlDateTimeUtils.toXmlGregorianCalendar(LocalDateTime.now().minusDays(60)));
        device.setDeviceType("MOBILE");
        return List.of(device);
    }
}