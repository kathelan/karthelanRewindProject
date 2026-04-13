package pl.kathelan.soap.contract;

import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;

/**
 * Shared setup utilities for SOAP producer-side contract tests.
 *
 * <p>Both {@link UserSoapClientContractTest} and {@link MobilePushProducerContractTest} need a
 * {@link WebServiceTemplate} pointed at a random-port server with WS-Security configured.
 * Extracting the factory here keeps the two tests DRY and ensures identical security settings.
 */
abstract class SoapContractTestBase {

    private static final String CONTEXT_PATH =
            "pl.kathelan.soap.api.generated:pl.kathelan.soap.push.generated";

    /**
     * Builds a {@link WebServiceTemplate} with a WSS4J UsernameToken interceptor configured
     * for the admin credentials used in all contract tests.
     */
    static WebServiceTemplate buildSecuredTemplate(int port) throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(CONTEXT_PATH);
        marshaller.afterPropertiesSet();

        Wss4jSecurityInterceptor wsSecurityInterceptor = new Wss4jSecurityInterceptor();
        wsSecurityInterceptor.setSecurementActions("UsernameToken");
        wsSecurityInterceptor.setSecurementUsername("admin");
        wsSecurityInterceptor.setSecurementPassword("adminpass");
        wsSecurityInterceptor.setSecurementPasswordType("PasswordText");
        wsSecurityInterceptor.afterPropertiesSet();

        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        template.setDefaultUri("http://localhost:" + port + "/ws");
        template.setInterceptors(new ClientInterceptor[]{wsSecurityInterceptor});
        return template;
    }

    /**
     * Builds a {@link WebServiceTemplate} without any security interceptor.
     * Used in tests that verify the server rejects unauthenticated requests.
     */
    static WebServiceTemplate buildUnsecuredTemplate(int port) throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(CONTEXT_PATH);
        marshaller.afterPropertiesSet();

        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        template.setDefaultUri("http://localhost:" + port + "/ws");
        return template;
    }
}
