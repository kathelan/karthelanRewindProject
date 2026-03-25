package pl.kathelan.soap.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;
import pl.kathelan.soap.client.config.SoapClientAutoConfiguration;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

@SpringBootTest(classes = SoapClientAutoConfiguration.class)
@TestPropertySource(properties = {
        "soap.client.url=http://localhost:8080/ws",
        "soap.client.username=admin",
        "soap.client.password=adminpass"
})
class MobilePushClientTest {

    private static final String NS = "http://kathelan.pl/soap/push";

    @Autowired
    private MobilePushClient client;

    @Autowired
    private WebServiceTemplate webServiceTemplate;

    private MockWebServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockWebServiceServer.createServer(webServiceTemplate);
    }

    @Test
    void shouldGetUserCapabilities() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:getUserCapabilitiesRequest xmlns:tns="%s">
                            <tns:userId>user-push</tns:userId>
                        </tns:getUserCapabilitiesRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:getUserCapabilitiesResponse xmlns:tns="%s">
                            <tns:userId>user-push</tns:userId>
                            <tns:active>true</tns:active>
                            <tns:authMethods>PUSH</tns:authMethods>
                        </tns:getUserCapabilitiesResponse>
                        """.formatted(NS))));

        GetUserCapabilitiesResponse response = client.getUserCapabilities("user-push");

        assertThat(response.getUserId()).isEqualTo("user-push");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getAuthMethods()).hasSize(1);
        assertThat(response.getErrorCode()).isNull();
        mockServer.verify();
    }

    @Test
    void shouldReturnUserNotFoundFromCapabilities() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:getUserCapabilitiesRequest xmlns:tns="%s">
                            <tns:userId>unknown</tns:userId>
                        </tns:getUserCapabilitiesRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:getUserCapabilitiesResponse xmlns:tns="%s">
                            <tns:errorCode>USER_NOT_FOUND</tns:errorCode>
                            <tns:message>User not found: 'unknown'</tns:message>
                        </tns:getUserCapabilitiesResponse>
                        """.formatted(NS))));

        GetUserCapabilitiesResponse response = client.getUserCapabilities("unknown");

        assertThat(response.getUserId()).isNull();
        assertThat(response.getErrorCode()).isNotNull();
        assertThat(response.getErrorCode().value()).isEqualTo("USER_NOT_FOUND");
        mockServer.verify();
    }

    @Test
    void shouldSendPushSuccessfully() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:sendPushRequest xmlns:tns="%s">
                            <tns:userId>user-push</tns:userId>
                            <tns:processId>proc-abc</tns:processId>
                        </tns:sendPushRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:sendPushResponse xmlns:tns="%s">
                            <tns:deliveryId>delivery-123</tns:deliveryId>
                            <tns:sendStatus>SENT</tns:sendStatus>
                        </tns:sendPushResponse>
                        """.formatted(NS))));

        SendPushResponse response = client.sendPush("user-push", "proc-abc");

        assertThat(response.getDeliveryId()).isEqualTo("delivery-123");
        assertThat(response.getSendStatus().value()).isEqualTo("SENT");
        assertThat(response.getErrorCode()).isNull();
        mockServer.verify();
    }

    @Test
    void shouldReturnUserInactiveOnSendPush() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:sendPushRequest xmlns:tns="%s">
                            <tns:userId>user-inactive</tns:userId>
                            <tns:processId>proc-xyz</tns:processId>
                        </tns:sendPushRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:sendPushResponse xmlns:tns="%s">
                            <tns:errorCode>USER_INACTIVE</tns:errorCode>
                            <tns:message>User is inactive: 'user-inactive'</tns:message>
                        </tns:sendPushResponse>
                        """.formatted(NS))));

        SendPushResponse response = client.sendPush("user-inactive", "proc-xyz");

        assertThat(response.getDeliveryId()).isNull();
        assertThat(response.getErrorCode().value()).isEqualTo("USER_INACTIVE");
        mockServer.verify();
    }

    @Test
    void shouldGetPushStatusPending() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:getPushStatusRequest xmlns:tns="%s">
                            <tns:deliveryId>delivery-123</tns:deliveryId>
                        </tns:getPushStatusRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:getPushStatusResponse xmlns:tns="%s">
                            <tns:deliveryId>delivery-123</tns:deliveryId>
                            <tns:pushStatus>PENDING</tns:pushStatus>
                        </tns:getPushStatusResponse>
                        """.formatted(NS))));

        GetPushStatusResponse response = client.getPushStatus("delivery-123");

        assertThat(response.getDeliveryId()).isEqualTo("delivery-123");
        assertThat(response.getPushStatus().value()).isEqualTo("PENDING");
        assertThat(response.getErrorCode()).isNull();
        mockServer.verify();
    }

    @Test
    void shouldReturnDeliveryNotFound() {
        mockServer
                .expect(payload(new StringSource("""
                        <tns:getPushStatusRequest xmlns:tns="%s">
                            <tns:deliveryId>ghost</tns:deliveryId>
                        </tns:getPushStatusRequest>
                        """.formatted(NS))))
                .andRespond(withPayload(new StringSource("""
                        <tns:getPushStatusResponse xmlns:tns="%s">
                            <tns:errorCode>DELIVERY_NOT_FOUND</tns:errorCode>
                            <tns:message>Delivery not found: 'ghost'</tns:message>
                        </tns:getPushStatusResponse>
                        """.formatted(NS))));

        GetPushStatusResponse response = client.getPushStatus("ghost");

        assertThat(response.getDeliveryId()).isNull();
        assertThat(response.getErrorCode().value()).isEqualTo("DELIVERY_NOT_FOUND");
        mockServer.verify();
    }
}