package pl.kathelan.soap.client.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SoapLoggingClientInterceptorTest {

    private final SoapLoggingClientInterceptor interceptor = new SoapLoggingClientInterceptor();

    // ===== masked() =====

    @Test
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

        String result = SoapLoggingClientInterceptor.masked(xml);

        assertThat(result)
                .contains("<wsse:Password Type=\"PasswordText\">****</wsse:Password>")
                .doesNotContain("secretpass");
    }

    @Test
    void masked_shouldHandlePasswordWithoutNamespacePrefix() {
        String xml = "<Security><Password>plainpass</Password></Security>";

        String result = SoapLoggingClientInterceptor.masked(xml);

        assertThat(result)
                .contains("<Password>****</Password>")
                .doesNotContain("plainpass");
    }

    @Test
    void masked_shouldLeaveXmlUnchangedWhenNoPasswordPresent() {
        String xml = "<soapenv:Envelope><soapenv:Body><request/></soapenv:Body></soapenv:Envelope>";

        assertThat(SoapLoggingClientInterceptor.masked(xml)).isEqualTo(xml);
    }

    @Test
    void masked_shouldMaskMultiplePasswordElements() {
        String xml = """
                <wsse:Password>pass1</wsse:Password>
                <wsse:Password>pass2</wsse:Password>""";

        String result = SoapLoggingClientInterceptor.masked(xml);

        assertThat(result)
                .doesNotContain("pass1")
                .doesNotContain("pass2")
                .containsPattern("<wsse:Password>\\*{4}</wsse:Password>");
    }

    // ===== handleRequest / handleResponse / handleFault =====

    @Test
    void handleRequest_shouldReturnTrue() throws Exception {
        MessageContext ctx = mockContextWithRequest();

        assertThat(interceptor.handleRequest(ctx)).isTrue();
    }

    @Test
    void handleResponse_shouldReturnTrue() throws Exception {
        MessageContext ctx = mockContextWithResponse();

        assertThat(interceptor.handleResponse(ctx)).isTrue();
    }

    @Test
    void handleFault_shouldReturnTrue() throws Exception {
        MessageContext ctx = mockContextWithResponse();

        assertThat(interceptor.handleFault(ctx)).isTrue();
    }

    @Test
    void handleRequest_shouldReturnTrueEvenWhenSerializationFails() throws Exception {
        WebServiceMessage request = mock(WebServiceMessage.class);
        doThrow(IOException.class).when(request).writeTo(any());
        MessageContext ctx = mock(MessageContext.class);
        when(ctx.getRequest()).thenReturn(request);

        assertThat(interceptor.handleRequest(ctx)).isTrue();
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