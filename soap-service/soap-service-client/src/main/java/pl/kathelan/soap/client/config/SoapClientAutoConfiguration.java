package pl.kathelan.soap.client.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import pl.kathelan.soap.client.UserSoapClient;
import pl.kathelan.soap.client.UserSoapClientImpl;
import pl.kathelan.soap.client.http.BasicAuthMessageSender;

@AutoConfiguration
@EnableConfigurationProperties(SoapClientProperties.class)
public class SoapClientAutoConfiguration {

    @Bean
    public Jaxb2Marshaller soapClientMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("pl.kathelan.soap.api.generated");
        return marshaller;
    }

    @Bean
    public WebServiceTemplate webServiceTemplate(SoapClientProperties properties,
                                                 Jaxb2Marshaller soapClientMarshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(soapClientMarshaller);
        template.setUnmarshaller(soapClientMarshaller);
        template.setDefaultUri(properties.getUrl());
        template.setMessageSender(new BasicAuthMessageSender(
                properties.getUsername(),
                properties.getPassword(),
                properties.getConnectTimeoutMs(),
                properties.getReadTimeoutMs()
        ));
        return template;
    }

    @Bean
    public UserSoapClient userSoapClient(WebServiceTemplate webServiceTemplate) {
        return new UserSoapClientImpl(webServiceTemplate);
    }
}
