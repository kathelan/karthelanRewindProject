package pl.kathelan.auth.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import pl.kathelan.auth.AuthServiceBaseTest;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.common.web.RequestLoggingInterceptor;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingInterceptorIntegrationTest extends AuthServiceBaseTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void requestLoggingInterceptor_beanIsPresentInAuthServiceContext() {
        assertThat(applicationContext.getBeansOfType(RequestLoggingInterceptor.class))
                .as("RequestLoggingInterceptor should be auto-configured in auth-service context")
                .isNotEmpty();
    }

    @Test
    void requestLoggingInterceptor_doesNotBreakGetCapabilities() {
        stubGetCapabilities("user1", true, pl.kathelan.soap.push.generated.AuthMethod.PUSH);

        doGet("/auth/capabilities/{userId}", "user1")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.userId").isEqualTo("user1");
    }

    @Test
    void requestLoggingInterceptor_doesNotBreakPostInit() {
        stubSendPush("delivery-log-test");

        doPost("/process/init", new InitProcessRequest("user-log", AuthMethod.PUSH))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.processId").isNotEmpty();
    }

    @Test
    void requestLoggingInterceptor_doesNotBreakNotFoundResponse() {
        doGet("/process/{id}/stream", "00000000-0000-0000-0000-000000000000")
                .expectStatus().isNotFound();
    }
}