package pl.kathelan.soap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "soap.security")
public class SoapUsersProperties {

    private Map<String, String> users = Map.of();
}
