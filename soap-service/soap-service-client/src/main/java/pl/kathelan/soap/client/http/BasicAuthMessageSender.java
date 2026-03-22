package pl.kathelan.soap.client.http;

import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public class BasicAuthMessageSender extends HttpUrlConnectionMessageSender {

    private final String authHeader;

    public BasicAuthMessageSender(String username, String password, int connectTimeoutMs, int readTimeoutMs) {
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        setConnectionTimeout(Duration.ofMillis(connectTimeoutMs));
        setReadTimeout(Duration.ofMillis(readTimeoutMs));
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection) throws IOException {
        connection.setRequestProperty("Authorization", authHeader);
        super.prepareConnection(connection);
    }
}
