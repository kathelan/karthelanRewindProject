package pl.kathelan.soap.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.client.MobilePushClientImpl;
import pl.kathelan.soap.client.UserSoapClient;
import pl.kathelan.soap.client.UserSoapClientImpl;
import pl.kathelan.soap.client.interceptor.SoapLoggingClientInterceptor;

import java.time.Duration;

@AutoConfiguration
@EnableConfigurationProperties(SoapClientProperties.class)
public class SoapClientAutoConfiguration {

    @Bean
    public Jaxb2Marshaller soapClientMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(
                "pl.kathelan.soap.api.generated:pl.kathelan.soap.push.generated"
        );
        return marshaller;
    }

    @Bean
    public WebServiceTemplate webServiceTemplate(SoapClientProperties properties,
                                                 Jaxb2Marshaller soapClientMarshaller) throws Exception {
        Wss4jSecurityInterceptor wsSecurityInterceptor = new Wss4jSecurityInterceptor();
        wsSecurityInterceptor.setSecurementActions("UsernameToken");
        wsSecurityInterceptor.setSecurementUsername(properties.getUsername());
        wsSecurityInterceptor.setSecurementPassword(properties.getPassword());
        wsSecurityInterceptor.setSecurementPasswordType("PasswordText");
        wsSecurityInterceptor.afterPropertiesSet();

        HttpUrlConnectionMessageSender sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        sender.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(soapClientMarshaller);
        template.setUnmarshaller(soapClientMarshaller);
        template.setDefaultUri(properties.getUrl());
        template.setMessageSender(sender);
        // Kolejność: security dodaje nagłówek WS-Security → logging widzi pełną kopertę (z hasłem zamaskowanym)
        template.setInterceptors(new ClientInterceptor[]{wsSecurityInterceptor, new SoapLoggingClientInterceptor()});
        return template;
    }

    @Bean
    public UserSoapClient userSoapClient(WebServiceTemplate webServiceTemplate) {
        return new UserSoapClientImpl(webServiceTemplate);
    }

    @Bean
    public MobilePushClient mobilePushClient(WebServiceTemplate webServiceTemplate) {
        return new MobilePushClientImpl(webServiceTemplate);
    }
}
