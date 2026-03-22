package pl.kathelan.soap.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "soap.client")
public class SoapClientProperties {

    private String url = "http://localhost:8080/ws";
    private String username;
    private String password;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
}
