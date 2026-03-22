package pl.kathelan.soap.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.wss4j.common.ext.WSPasswordCallback;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.util.Map;

/**
 * WSS4J CallbackHandler — dostarcza hasło dla UsernameToken z YAML-a.
 */
@Slf4j
@RequiredArgsConstructor
public class SoapSecurityCallbackHandler implements CallbackHandler {

    private final Map<String, String> users;

    @Override
    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof WSPasswordCallback pc) {
                String password = users.get(pc.getIdentifier());
                if (password != null) {
                    pc.setPassword(password);
                } else {
                    log.warn("WS-Security: unknown username '{}'", pc.getIdentifier());
                }
            } else {
                throw new UnsupportedCallbackException(callback, "Unsupported callback: " + callback.getClass());
            }
        }
    }
}
