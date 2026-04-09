package pl.kathelan.auth.contract;

import org.junit.jupiter.api.Test;
import pl.kathelan.auth.AuthServiceBaseTest;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;

import static org.mockito.Mockito.when;

/**
 * Alternatywa dla WireMock: JAXB parsuje XML z pliku → @MockitoBean zwraca gotowy obiekt.
 * Bez serwera HTTP — lżejszy test, te same dane co w fixtures.
 */
class CapabilitiesXmlFixtureTest extends AuthServiceBaseTest {

    @Test
    void getCapabilities_parsesFixtureXmlAndReturnsMappedResponse() throws Exception {
        GetUserCapabilitiesResponse soapResponse = loadFixture(
                "fixtures/get-capabilities-response.xml", GetUserCapabilitiesResponse.class);
        when(mobilePushClient.getUserCapabilities("user-fixture")).thenReturn(soapResponse);

        doGet("/auth/capabilities/{userId}", "user-fixture")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("user-fixture")
                .jsonPath("$.data.active").isEqualTo(true)
                .jsonPath("$.data.accountStatus").isEqualTo("ACTIVE")
                .jsonPath("$.data.authMethods.length()").isEqualTo(2)
                .jsonPath("$.data.devices[0].deviceId").isEqualTo("fixture-device-001")
                .jsonPath("$.data.devices[0].status").isEqualTo("ACTIVE");
    }
}
