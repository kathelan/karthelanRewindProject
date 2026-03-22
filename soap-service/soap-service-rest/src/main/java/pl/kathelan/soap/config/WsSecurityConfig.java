package pl.kathelan.soap.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

/**
 * Konfiguracja WS-Security (message-level) opartego na WSS4J UsernameToken.
 * Zastępuje transport-level HTTP Basic Auth — uwierzytelnianie jest częścią komunikatu SOAP.
 */
@Configuration
@RequiredArgsConstructor
public class WsSecurityConfig {

    private final SoapUsersProperties properties;

    @Bean
    public Wss4jSecurityInterceptor wsSecurityInterceptor() {
        Wss4jSecurityInterceptor interceptor = new Wss4jSecurityInterceptor();
        interceptor.setValidationActions("UsernameToken");
        interceptor.setValidationCallbackHandler(new SoapSecurityCallbackHandler(properties.getUsers()));
        return interceptor;
    }
}
