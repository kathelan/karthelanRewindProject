package pl.kathelan.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerRegistry;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;
import pl.kathelan.soap.client.UserSoapClient;
import pl.kathelan.user.exception.UserAlreadyExistsException;
import pl.kathelan.user.exception.UserNotFoundException;
import pl.kathelan.user.exception.UserServiceException;
import pl.kathelan.user.mapper.UserRestMapper;
import pl.kathelan.user.service.UserService;
import pl.kathelan.user.service.UserServiceImpl;

import java.time.Duration;
import java.util.Set;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return new CircuitBreakerRegistry(new InMemoryCircuitBreakerStateRepository());
    }

    @Bean
    public CircuitBreaker soapServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = new CircuitBreakerConfig(
                5,
                Duration.ofSeconds(10),
                // błędy domenowe nie otwierają CB — tylko awarie infrastrukturalne
                e -> !(e instanceof UserNotFoundException || e instanceof UserAlreadyExistsException)
        );
        return registry.getOrCreate("soap-service", config);
    }

    @Bean
    public RetryExecutor retryExecutor() {
        return new RetryExecutor();
    }

    @Bean
    public RetryConfig soapServiceRetryConfig() {
        // Retry tylko na wyjątkach infrastrukturalnych — domenowe (UserNotFoundException, UserAlreadyExistsException)
        // nie są powtarzane, bo SOAP zwrócił poprawną odpowiedź biznesową
        return new RetryConfig(3, Duration.ofMillis(200), 2.0,
                Set.of(RuntimeException.class),
                Set.of(UserNotFoundException.class, UserAlreadyExistsException.class, UserServiceException.class));
    }

    @Bean
    public ResilientCaller soapServiceResilientCaller(CircuitBreaker soapServiceCircuitBreaker,
                                                      RetryExecutor retryExecutor,
                                                      RetryConfig soapServiceRetryConfig) {
        return new ResilientCaller(soapServiceCircuitBreaker, retryExecutor, soapServiceRetryConfig);
    }

    @Bean
    public UserService userService(UserSoapClient soapClient,
                                   UserRestMapper mapper,
                                   ResilientCaller soapServiceResilientCaller) {
        return new UserServiceImpl(soapClient, mapper, soapServiceResilientCaller);
    }
}
