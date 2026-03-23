package pl.kathelan.common.resilience.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.common.resilience.exception.CircuitOpenException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerRegistryTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreakerConfig config;

    @BeforeEach
    void setUp() {
        registry = new CircuitBreakerRegistry(new InMemoryCircuitBreakerStateRepository());
        config = new CircuitBreakerConfig(3, Duration.ofSeconds(10), e -> true);
    }

    @Test
    void shouldCreateNewCircuitBreakerForUnknownService() {
        CircuitBreaker cb = registry.getOrCreate("soap-service", config);

        assertThat(cb).isNotNull();
    }

    @Test
    void shouldReturnSameInstanceForSameService() {
        CircuitBreaker first = registry.getOrCreate("soap-service", config);
        CircuitBreaker second = registry.getOrCreate("soap-service", config);

        assertThat(first).isSameAs(second);
    }

    @Test
    void shouldIsolateCBsBetweenServices() {
        CircuitBreaker soapCb = registry.getOrCreate("soap-service", config);
        CircuitBreaker productCb = registry.getOrCreate("product-service", config);

        assertThat(soapCb).isNotSameAs(productCb);
    }

    @Test
    void shouldMaintainStateAcrossCallsToGetOrCreate() {
        CircuitBreaker cb = registry.getOrCreate("soap-service", config);
        triggerFailures(cb, 3);

        CircuitBreaker sameCb = registry.getOrCreate("soap-service", config);
        assertThatThrownBy(() -> sameCb.execute(() -> "ok"))
                .isInstanceOf(CircuitOpenException.class);
    }

    private void triggerFailures(CircuitBreaker cb, int count) {
        for (int i = 0; i < count; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("down"); });
            } catch (Exception ignored) {}
        }
    }
}
