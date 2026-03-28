package pl.kathelan.auth.simulator;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import pl.kathelan.soap.SoapServiceApplication;

/**
 * JUnit 5 extension that starts soap-service with local+simulator profiles
 * on a random port. Use as a static @RegisterExtension field so it starts
 * before @DynamicPropertySource resolves the SOAP client URL.
 */
public class SoapSimulatorExtension implements BeforeAllCallback, AfterAllCallback {

    private ConfigurableApplicationContext context;
    private int port;

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        context = new SpringApplicationBuilder(SoapServiceApplication.class)
                .profiles("local", "simulator")
                .properties(
                        "server.port=0",
                        "soap.security.users.admin=adminpass"
                )
                .run();
        port = context.getEnvironment().getProperty("local.server.port", Integer.class);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        if (context != null) {
            context.close();
        }
    }

    public String getSoapUrl() {
        return "http://localhost:" + port + "/ws";
    }

    public String getSimulatorBaseUrl() {
        return "http://localhost:" + port;
    }
}
