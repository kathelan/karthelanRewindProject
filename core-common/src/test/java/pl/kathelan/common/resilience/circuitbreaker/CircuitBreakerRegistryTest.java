package pl.kathelan.common.resilience.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.kathelan.common.resilience.exception.CircuitOpenException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CircuitBreakerRegistry — globalny rejestr Circuit Breakerów, jeden per serwis.
 *
 * Problem który rozwiązuje: gdyby każdy komponent tworzył własny CB dla tego samego
 * serwisu, stan (liczba błędów, OPEN/CLOSED) nie byłby współdzielony.
 * Registry gwarantuje że dla danej nazwy serwisu zawsze istnieje dokładnie jeden CB.
 *
 * Thread-safe: używa ConcurrentHashMap + computeIfAbsent.
 */
@DisplayName("CircuitBreakerRegistry")
class CircuitBreakerRegistryTest {

    private CircuitBreakerRegistry registry;
    private CircuitBreakerConfig config;

    @BeforeEach
    void setUp() {
        registry = new CircuitBreakerRegistry(new InMemoryCircuitBreakerStateRepository());
        config = new CircuitBreakerConfig(3, Duration.ofSeconds(10), e -> true);
    }

    @Nested
    @DisplayName("Tworzenie i pobieranie Circuit Breakerów")
    class GettingCircuitBreakers {

        @Test
        @DisplayName("tworzy nowy CB gdy serwis pojawia się po raz pierwszy")
        void createsNewCbForUnknownService() {
            CircuitBreaker cb = registry.getOrCreate("soap-service", config);

            assertThat(cb).isNotNull();
        }

        @Test
        @DisplayName("zwraca ten sam obiekt CB przy kolejnych wywołaniach dla tego samego serwisu")
        void returnsSameInstanceForSameService() {
            CircuitBreaker first = registry.getOrCreate("soap-service", config);
            CircuitBreaker second = registry.getOrCreate("soap-service", config);

            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("tworzy osobny CB dla każdego serwisu — awaria jednego nie wpływa na drugi")
        void isolatesCbsBetweenServices() {
            CircuitBreaker soapCb = registry.getOrCreate("soap-service", config);
            CircuitBreaker productCb = registry.getOrCreate("product-service", config);

            assertThat(soapCb).isNotSameAs(productCb);
        }
    }

    @Nested
    @DisplayName("Zachowanie stanu CB między wywołaniami registry")
    class StatePreservation {

        @Test
        @DisplayName("stan CB (np. OPEN po błędach) jest zachowany przy ponownym pobraniu z registry")
        void maintainsStateAcrossGetOrCreateCalls() {
            CircuitBreaker cb = registry.getOrCreate("soap-service", config);
            // Otwieramy CB przez 3 błędy
            triggerFailures(cb, 3);

            // Pobieramy "ponownie" — to ten sam obiekt, powinien pamiętać że jest OPEN
            CircuitBreaker sameCb = registry.getOrCreate("soap-service", config);

            assertThatThrownBy(() -> sameCb.execute(() -> "ok"))
                    .isInstanceOf(CircuitOpenException.class);
        }
    }

    private void triggerFailures(CircuitBreaker cb, int count) {
        for (int i = 0; i < count; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("serwis nie odpowiada"); });
            } catch (Exception ignored) {}
        }
    }
}
