package pl.kathelan.soap.interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SoapLoggingInterceptor — unit tests for SOAP message logging and password masking.
 *
 * <p>Two responsibilities are tested:
 * <ol>
 *   <li>{@code masked()} — static helper that redacts {@code <Password>} element values
 *       in raw XML strings before they reach any log output.</li>
 *   <li>Interceptor lifecycle methods ({@code handleRequest}, {@code handleResponse},
 *       {@code handleFault}) — must always return {@code true} so the processing chain
 *       continues, even when serialisation of the message fails.</li>
 * </ol>
 */
@DisplayName("SoapLoggingInterceptor — password masking and interceptor lifecycle")
class SoapLoggingInterceptorTest {

    private final SoapLoggingInterceptor interceptor = new SoapLoggingInterceptor();

    // ===== masked() =====

    @Nested
    @DisplayName("masked() — redacting password values in XML strings")
    class Masked {

        /**
         * Standard WS-Security password: a {@code <wsse:Password>} element with a Type attribute
         * must have its text content replaced with {@code ****} while the tag and attribute are kept.
         * The original plaintext password must not appear anywhere in the masked output.
         */
        @Test
        @DisplayName("replaces wsse:Password content with **** while preserving tag and Type attribute")
        void masked_shouldReplacePasswordWithStars() {
            String xml = """
                    <soapenv:Envelope xmlns:wsse="http://...wssecurity">
                        <soapenv:Header>
                            <wsse:Security>
                                <wsse:UsernameToken>
                                    <wsse:Username>admin</wsse:Username>
                                    <wsse:Password Type="PasswordText">secretpass</wsse:Password>
                                </wsse:UsernameToken>
                            </wsse:Security>
                        </soapenv:Header>
                    </soapenv:Envelope>""";

            String result = SoapLoggingInterceptor.masked(xml);

            assertThat(result)
                    .contains("<wsse:Password Type=\"PasswordText\">****</wsse:Password>")
                    .doesNotContain("secretpass");
        }

        /**
         * Password element without namespace prefix: the masker must also handle
         * unprefixed {@code <Password>} tags, which may appear in simplified or legacy payloads.
         */
        @Test
        @DisplayName("masks unprefixed <Password> element as well as namespaced variants")
        void masked_shouldHandlePasswordWithoutNamespacePrefix() {
            String xml = "<Security><Password>plainpass</Password></Security>";

            String result = SoapLoggingInterceptor.masked(xml);

            assertThat(result)
                    .contains("<Password>****</Password>")
                    .doesNotContain("plainpass");
        }

        /**
         * No password present: XML that contains no {@code <Password>} element must pass through
         * the masker unchanged. This guards against accidentally mutating legitimate payload fields.
         */
        @Test
        @DisplayName("leaves XML unchanged when no Password element is present")
        void masked_shouldLeaveXmlUnchangedWhenNoPasswordPresent() {
            String xml = "<soapenv:Envelope><soapenv:Body><request/></soapenv:Body></soapenv:Envelope>";

            assertThat(SoapLoggingInterceptor.masked(xml)).isEqualTo(xml);
        }

        /**
         * Multiple password elements: when the XML contains more than one {@code <Password>}
         * element (e.g. nested tokens), all of them must be masked. None of the original
         * plaintext values should remain in the output.
         */
        @Test
        @DisplayName("masks all Password elements when multiple appear in the same XML string")
        void masked_shouldMaskMultiplePasswordElements() {
            String xml = """
                    <wsse:Password>pass1</wsse:Password>
                    <wsse:Password>pass2</wsse:Password>""";

            String result = SoapLoggingInterceptor.masked(xml);

            assertThat(result)
                    .doesNotContain("pass1")
                    .doesNotContain("pass2")
                    .containsPattern("<wsse:Password>\\*{4}</wsse:Password>");
        }
    }

    // ===== handleRequest / handleResponse / handleFault =====

    @Nested
    @DisplayName("interceptor lifecycle — handleRequest, handleResponse, handleFault")
    class Lifecycle {

        /**
         * handleRequest must return {@code true} to allow the request processing chain to continue.
         * Returning false would cause Spring-WS to short-circuit the endpoint call.
         */
        @Test
        @DisplayName("handleRequest returns true so the processing chain continues")
        void handleRequest_shouldReturnTrue() throws Exception {
            MessageContext ctx = mockContextWithRequest();

            assertThat(interceptor.handleRequest(ctx, new Object())).isTrue();
        }

        /**
         * handleResponse must return {@code true} to allow the response to be sent to the client.
         */
        @Test
        @DisplayName("handleResponse returns true so the response is forwarded")
        void handleResponse_shouldReturnTrue() throws Exception {
            MessageContext ctx = mockContextWithResponse();

            assertThat(interceptor.handleResponse(ctx, new Object())).isTrue();
        }

        /**
         * handleFault must return {@code true} even on a SOAP fault so the fault is properly
         * propagated to the client rather than being silently swallowed.
         */
        @Test
        @DisplayName("handleFault returns true so the SOAP fault is propagated")
        void handleFault_shouldReturnTrue() throws Exception {
            MessageContext ctx = mockContextWithResponse();

            assertThat(interceptor.handleFault(ctx, new Object())).isTrue();
        }

        /**
         * Serialisation failure resilience: when writing the request message to a stream throws
         * an {@link IOException} (e.g. the message source is unavailable), the interceptor must
         * still return {@code true} and not propagate the exception — logging is best-effort.
         */
        @Test
        @DisplayName("handleRequest returns true even when message serialisation fails with IOException")
        void handleRequest_shouldReturnTrueEvenWhenSerializationFails() throws Exception {
            WebServiceMessage request = mock(WebServiceMessage.class);
            doThrow(IOException.class).when(request).writeTo(any());
            MessageContext ctx = mock(MessageContext.class);
            when(ctx.getRequest()).thenReturn(request);

            assertThat(interceptor.handleRequest(ctx, new Object())).isTrue();
        }

        /**
         * writeTo is invoked: handleFault always logs at WARN level (not guarded by isDebugEnabled),
         * so {@code writeTo} on the response message must be called unconditionally.
         * If the call were removed, the fault XML in the log would be empty, hiding diagnostic info.
         */
        @Test
        @DisplayName("handleFault calls writeTo on the response message to produce the fault XML")
        void handleFault_shouldCallWriteToOnMessage() throws Exception {
            WebServiceMessage response = mock(WebServiceMessage.class);
            doAnswer(inv -> {
                OutputStream out = inv.getArgument(0);
                out.write("<fault/>".getBytes(StandardCharsets.UTF_8));
                return null;
            }).when(response).writeTo(any());
            MessageContext ctx = mock(MessageContext.class);
            when(ctx.getResponse()).thenReturn(response);

            interceptor.handleFault(ctx, new Object());

            verify(response).writeTo(any());
        }
    }

    // ===== toXml() =====

    @Nested
    @DisplayName("toXml() — serialising a WebServiceMessage to a string")
    class ToXml {

        /**
         * When a message serialises successfully, {@code toXml} must return its byte content
         * as a UTF-8 string. This guards the mutation "replace return value with empty string" —
         * if that mutation lived, the interceptor would log an empty string instead of the real XML.
         */
        @Test
        @DisplayName("returns the XML content written by the message when serialisation succeeds")
        void toXml_shouldReturnXmlContentWhenSerializationSucceeds() throws IOException {
            WebServiceMessage message = mock(WebServiceMessage.class);
            doAnswer(inv -> {
                OutputStream out = inv.getArgument(0);
                out.write("<request/>".getBytes(StandardCharsets.UTF_8));
                return null;
            }).when(message).writeTo(any());

            String result = interceptor.toXml(message);

            assertThat(result).isEqualTo("<request/>");
        }

        /**
         * When {@code writeTo} throws {@link IOException} (e.g. message source is unavailable),
         * {@code toXml} must return the fallback sentinel string rather than propagating the
         * exception. Logging is best-effort and must never interrupt request processing.
         */
        @Test
        @DisplayName("returns sentinel string when message serialisation throws IOException")
        void toXml_shouldReturnFallbackWhenSerializationFails() throws IOException {
            WebServiceMessage message = mock(WebServiceMessage.class);
            doThrow(IOException.class).when(message).writeTo(any());

            String result = interceptor.toXml(message);

            assertThat(result).isEqualTo("<could not serialize message>");
        }
    }

    // ===== helpers =====

    private MessageContext mockContextWithRequest() throws IOException {
        WebServiceMessage request = mock(WebServiceMessage.class);
        MessageContext ctx = mock(MessageContext.class);
        when(ctx.getRequest()).thenReturn(request);
        return ctx;
    }

    private MessageContext mockContextWithResponse() throws IOException {
        WebServiceMessage response = mock(WebServiceMessage.class);
        MessageContext ctx = mock(MessageContext.class);
        when(ctx.getResponse()).thenReturn(response);
        return ctx;
    }
}
