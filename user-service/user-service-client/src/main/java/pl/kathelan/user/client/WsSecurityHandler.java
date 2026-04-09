package pl.kathelan.user.client;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;

import pl.kathelan.user.client.exception.WsSecurityException;

import javax.xml.namespace.QName;
import java.util.Set;

/**
 * JAX-WS SOAPHandler dodający nagłówek WS-Security z UsernameToken do każdego wychodzącego komunikatu.
 * Alternatywa dla HTTP Basic Auth — auth jest częścią komunikatu SOAP, nie nagłówka HTTP.
 */
public class WsSecurityHandler implements SOAPHandler<SOAPMessageContext> {

    private static final String WSSE_NS =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String PW_TEXT_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";

    private final String username;
    private final String password;

    public WsSecurityHandler(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public boolean handleMessage(SOAPMessageContext ctx) {
        boolean outbound = (Boolean) ctx.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        if (outbound) {
            try {
                addSecurityHeader(ctx.getMessage());
            } catch (SOAPException e) {
                throw new WsSecurityException("Failed to add WS-Security header", e);
            }
        }
        return true;
    }

    private void addSecurityHeader(SOAPMessage message) throws SOAPException {
        SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();
        SOAPHeader header = envelope.getHeader();
        if (header == null) {
            header = envelope.addHeader();
        }

        SOAPElement security = header.addChildElement("Security", "wsse", WSSE_NS);
        SOAPElement usernameToken = security.addChildElement("UsernameToken", "wsse");
        usernameToken.addChildElement("Username", "wsse").setTextContent(username);

        SOAPElement passwordEl = usernameToken.addChildElement("Password", "wsse");
        passwordEl.setAttribute("Type", PW_TEXT_TYPE);
        passwordEl.setTextContent(password);
    }

    @Override
    public boolean handleFault(SOAPMessageContext ctx) {
        return true;
    }

    @Override
    public void close(MessageContext ctx) {
    }

    @Override
    public Set<QName> getHeaders() {
        return Set.of();
    }
}
