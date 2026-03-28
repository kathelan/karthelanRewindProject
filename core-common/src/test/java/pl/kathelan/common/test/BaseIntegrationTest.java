package pl.kathelan.common.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "soap.client.username=test",
        "soap.client.password=test"
})
public abstract class BaseIntegrationTest {

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected ObjectMapper objectMapper;

    protected WebTestClient.ResponseSpec doGet(String url, Object... uriVars) {
        return webTestClient.get().uri(url, uriVars).exchange();
    }

    protected WebTestClient.ResponseSpec doGetSse(String url, Object... uriVars) {
        return webTestClient.get().uri(url, uriVars)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange();
    }

    protected WebTestClient.ResponseSpec doPost(String url, Object body) {
        return webTestClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    protected WebTestClient.ResponseSpec doPatch(String url, Object... uriVars) {
        return webTestClient.patch().uri(url, uriVars).exchange();
    }

    protected WebTestClient.ResponseSpec doPatchWithBody(String url, Object body, Object... uriVars) {
        return webTestClient.patch().uri(url, uriVars)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    protected WebTestClient.ResponseSpec doPut(String url, Object body, Object... uriVars) {
        return webTestClient.put().uri(url, uriVars)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    protected WebTestClient.ResponseSpec doDelete(String url, Object... uriVars) {
        return webTestClient.delete().uri(url, uriVars).exchange();
    }
}