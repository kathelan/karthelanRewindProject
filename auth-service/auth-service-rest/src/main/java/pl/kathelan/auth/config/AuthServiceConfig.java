package pl.kathelan.auth.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.kathelan.auth.domain.repository.AuthProcessRepository;
import pl.kathelan.auth.service.AuthProcessServiceImpl;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CountBasedCircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;
import pl.kathelan.soap.client.MobilePushClient;

import java.time.Duration;
import java.util.Set;

@Configuration
public class AuthServiceConfig {

    @Bean
    public CountBasedCircuitBreaker mobilePushCircuitBreaker() {
        return new CountBasedCircuitBreaker(
                "mobile-push",
                new CircuitBreakerConfig(5, Duration.ofSeconds(30), e -> true),
                new InMemoryCircuitBreakerStateRepository()
        );
    }

    @Bean
    public ResilientCaller mobilePushResilientCaller(CountBasedCircuitBreaker mobilePushCircuitBreaker) {
        return new ResilientCaller(
                mobilePushCircuitBreaker,
                new RetryExecutor(),
                new RetryConfig(3, Duration.ofMillis(500), 2.0, Set.of(RuntimeException.class))
        );
    }

    @Bean
    public AuthProcessServiceImpl authProcessService(
            AuthProcessRepository repository,
            MobilePushClient mobilePushClient,
            ResilientCaller mobilePushResilientCaller,
            ApplicationEventPublisher eventPublisher) {
        return new AuthProcessServiceImpl(repository, mobilePushClient, mobilePushResilientCaller, eventPublisher);
    }
}