package pl.kathelan.soap.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.List;

@EnableWs
@Configuration
@RequiredArgsConstructor
public class WebServiceConfig extends WsConfigurerAdapter {

    private final Wss4jSecurityInterceptor wsSecurityInterceptor;

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        // Kolejność ma znaczenie: najpierw auth, potem walidacja
        interceptors.add(wsSecurityInterceptor);
        interceptors.add(payloadValidatingInterceptor());
    }

    @Bean
    public PayloadValidatingInterceptor payloadValidatingInterceptor() {
        PayloadValidatingInterceptor interceptor = new PayloadValidatingInterceptor();
        interceptor.setSchema(new ClassPathResource("users.xsd"));
        interceptor.setValidateRequest(true);
        interceptor.setValidateResponse(false);
        return interceptor;
    }

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "users")
    public DefaultWsdl11Definition defaultWsdl11Definition(XsdSchema usersSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("UsersPort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace("http://kathelan.pl/soap/users");
        definition.setSchema(usersSchema);
        return definition;
    }

    @Bean
    public XsdSchema usersSchema() {
        return new SimpleXsdSchema(new ClassPathResource("users.xsd"));
    }
}