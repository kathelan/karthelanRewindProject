package pl.kathelan.auth.contract;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.auth.service.AuthProcessSchedulerService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Contract tests: verify auth-service sends correct SOAP envelopes over HTTP
 * and correctly parses SOAP responses from the MobilePush service.
 * <p>
 * Unlike unit tests (which mock MobilePushClient at Java level), these tests
 * go through the actual Spring-WS WebServiceTemplate + JAXB marshalling layer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
@TestPropertySource(properties = {"soap.client.username=test", "soap.client.password=test"})
class MobilePushContractTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void overrideSoapUrl(DynamicPropertyRegistry registry) {
        registry.add("soap.client.url", () -> wm.baseUrl() + "/ws");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private InMemoryAuthProcessRepository repository;

    @Autowired
    private AuthProcessSchedulerService schedulerService;

    @BeforeEach
    void reset() {
        repository.clear();
    }

    @Test
    void sendPush_sendsCorrectSoapRequestAndParsesResponse() throws Exception {
        wm.stubFor(post(urlEqualTo("/ws"))
                .withRequestBody(containing("sendPushRequest"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(loadXml("contract/send-push-response.xml"))));

        webTestClient.post().uri("/process/init")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new InitProcessRequest("user-contract", AuthMethod.PUSH))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.processId").isNotEmpty()
                .jsonPath("$.data.expiresAt").isNotEmpty();

        wm.verify(postRequestedFor(urlEqualTo("/ws"))
                .withRequestBody(containing("sendPushRequest"))
                .withRequestBody(WireMock.matchingXPath("//*[local-name()='userId' and text()='user-contract']"))
                .withRequestBody(WireMock.matchingXPath("//*[local-name()='processId']")));
    }

    @Test
    void getUserCapabilities_sendsCorrectSoapRequestAndParsesResponse() throws Exception {
        wm.stubFor(post(urlEqualTo("/ws"))
                .withRequestBody(containing("getUserCapabilitiesRequest"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(loadXml("contract/get-capabilities-response.xml"))));

        webTestClient.get().uri("/auth/capabilities/user-contract")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("user-contract")
                .jsonPath("$.data.active").isEqualTo(true)
                .jsonPath("$.data.authMethods[0]").isEqualTo("PUSH");

        wm.verify(postRequestedFor(urlEqualTo("/ws"))
                .withRequestBody(containing("getUserCapabilitiesRequest"))
                .withRequestBody(containing("user-contract")));
    }

    @Test
    void getPushStatus_sendsCorrectSoapRequestAndParsesResponse() throws Exception {
        // Step 1: stub sendPush + init process → creates process with deliveryId=delivery-contract-1
        wm.stubFor(post(urlEqualTo("/ws"))
                .withRequestBody(containing("sendPushRequest"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(loadXml("contract/send-push-response.xml"))));

        webTestClient.post().uri("/process/init")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new InitProcessRequest("user-contract", AuthMethod.PUSH))
                .exchange()
                .expectStatus().isCreated();

        // Step 2: stub getPushStatus response
        wm.stubFor(post(urlEqualTo("/ws"))
                .withRequestBody(containing("getPushStatusRequest"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(loadXml("contract/get-push-status-approved-response.xml"))));

        // Step 3: trigger scheduler — causes actual SOAP getPushStatus call through WebServiceTemplate
        schedulerService.pollAndUpdatePushStatuses();

        // Step 4: verify correct SOAP envelope was sent with the deliveryId from the process
        wm.verify(postRequestedFor(urlEqualTo("/ws"))
                .withRequestBody(containing("getPushStatusRequest"))
                .withRequestBody(WireMock.matchingXPath("//*[local-name()='deliveryId' and text()='delivery-contract-1']")));
    }

    @Test
    void sendPush_includesWsSecurityHeader() throws Exception {
        wm.stubFor(post(urlEqualTo("/ws"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "text/xml;charset=UTF-8")
                        .withBody(loadXml("contract/send-push-response.xml"))));

        webTestClient.post().uri("/process/init")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new InitProcessRequest("user-contract", AuthMethod.PUSH))
                .exchange()
                .expectStatus().isCreated();

        wm.verify(postRequestedFor(urlEqualTo("/ws"))
                .withRequestBody(containing("Security"))
                .withRequestBody(containing("UsernameToken")));
    }

    private String loadXml(String path) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Resource not found: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
