package pl.kathelan.soap.client.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.client.WebServiceClientException;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.context.MessageContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Loguje wychodzące żądania i przychodzące odpowiedzi SOAP po stronie klienta.
 * Rejestrowany po Wss4jSecurityInterceptor, więc widzi pełną kopertę z nagłówkiem WS-Security.
 * Hasła są maskowane przed zapisem do logu.
 * Logowanie aktywne na poziomie DEBUG.
 */
@Slf4j
public class SoapLoggingClientInterceptor implements ClientInterceptor {

    static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(<(?:[\\w]*:)?Password[^>]*>)[^<]*(</(?:[\\w]*:)?Password>)"
    );

    @Override
    public boolean handleRequest(MessageContext messageContext) throws WebServiceClientException {
        if (log.isDebugEnabled()) {
            log.debug("SOAP Client Request:\n{}", masked(toXml(messageContext.getRequest())));
        }
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext) throws WebServiceClientException {
        if (log.isDebugEnabled()) {
            log.debug("SOAP Client Response:\n{}", toXml(messageContext.getResponse()));
        }
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext) throws WebServiceClientException {
        log.warn("SOAP Client Fault:\n{}", toXml(messageContext.getResponse()));
        return true;
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Exception ex) throws WebServiceClientException {
    }

    static String masked(String xml) {
        return PASSWORD_PATTERN.matcher(xml).replaceAll("$1****$2");
    }

    private String toXml(WebServiceMessage message) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<could not serialize message>";
        }
    }
}