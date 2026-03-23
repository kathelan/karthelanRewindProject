package pl.kathelan.soap.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.EndpointInterceptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Loguje przychodzące żądania i wychodzące odpowiedzi SOAP.
 * Hasła w nagłówkach WS-Security są maskowane przed zapisem do logu.
 * Logowanie aktywne na poziomie DEBUG.
 */
@Slf4j
@Component
public class SoapLoggingInterceptor implements EndpointInterceptor {

    static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(<(?:[\\w]*:)?Password[^>]*>)[^<]*(</(?:[\\w]*:)?Password>)"
    );

    @Override
    public boolean handleRequest(MessageContext messageContext, Object endpoint) {
        if (log.isDebugEnabled()) {
            log.debug("SOAP Request:\n{}", masked(toXml(messageContext.getRequest())));
        }
        return true;
    }

    @Override
    public boolean handleResponse(MessageContext messageContext, Object endpoint) {
        if (log.isDebugEnabled()) {
            log.debug("SOAP Response:\n{}", toXml(messageContext.getResponse()));
        }
        return true;
    }

    @Override
    public boolean handleFault(MessageContext messageContext, Object endpoint) {
        log.warn("SOAP Fault:\n{}", toXml(messageContext.getResponse()));
        return true;
    }

    @Override
    public void afterCompletion(MessageContext messageContext, Object endpoint, Exception ex) {
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