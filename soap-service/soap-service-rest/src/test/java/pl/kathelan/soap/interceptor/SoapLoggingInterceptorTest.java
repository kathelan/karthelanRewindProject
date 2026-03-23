package pl.kathelan.soap.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.context.MessageContext;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SoapLoggingInterceptorTest {

    private final SoapLoggingInterceptor interceptor = new SoapLoggingInterceptor();

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

        String result = SoapLoggingInterceptor.masked(xml);

        assertThat(result)
                .contains("<wsse:Password Type=\"PasswordText\">****</wsse:Password>")
                .doesNotContain("secretpass");
    }

    @Test
    void masked_shouldHandlePasswordWithoutNamespacePrefix() {
        String xml = "<Security><Password>plainpass</Password></Security>";

        String result = SoapLoggingInterceptor.masked(xml);

        assertThat(result)
                .contains("<Password>****</Password>")
                .doesNotContain("plainpass");
    }

    @Test
    void masked_shouldLeaveXmlUnchangedWhenNoPasswordPresent() {
        String xml = "<soapenv:Envelope><soapenv:Body><request/></soapenv:Body></soapenv:Envelope>";

        assertThat(SoapLoggingInterceptor.masked(xml)).isEqualTo(xml);
    }

    @Test
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

    // ===== handleRequest / handleResponse / handleFault =====

    @Test
    void handleRequest_shouldReturnTrue() throws Exception {
        MessageContext ctx = mockContextWithRequest();

        assertThat(interceptor.handleRequest(ctx, new Object())).isTrue();
    }

    @Test
    void handleResponse_shouldReturnTrue() throws Exception {
        MessageContext ctx = mockContextWithResponse();

        assertThat(interceptor.handleResponse(ctx, new Object())).isTrue();
    }

    @Test
    void handleFault_shouldReturnTrue() throws Exception {
        MessageContext ctx = mockContextWithResponse();

        assertThat(interceptor.handleFault(ctx, new Object())).isTrue();
    }

    @Test
    void handleRequest_shouldReturnTrueEvenWhenSerializationFails() throws Exception {
        WebServiceMessage request = mock(WebServiceMessage.class);
        doThrow(IOException.class).when(request).writeTo(any());
        MessageContext ctx = mock(MessageContext.class);
        when(ctx.getRequest()).thenReturn(request);

        assertThat(interceptor.handleRequest(ctx, new Object())).isTrue();
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