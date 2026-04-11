package pl.kathelan.common.resilience.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemoryCircuitBreakerStateRepository — przechowuje stan CB w pamięci (ConcurrentHashMap).
 *
 * Stan CB (CLOSED/OPEN/HALF_OPEN + liczba błędów + czas otwarcia) musi być
 * przechowywany poza samym CB, żeby:
 *   - dało się go łatwo podmienić na implementację bazodanową (Redis, SQL)
 *   - CB mógł być bezstanowy i łatwiejszy do testowania
 *
 * Ta implementacja służy do dev/testów — nie przeżywa restartu aplikacji.
 */
@DisplayName("InMemoryCircuitBreakerStateRepository")
class InMemoryCircuitBreakerStateRepositoryTest {

    private InMemoryCircuitBreakerStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCircuitBreakerStateRepository();
    }

    @Nested
    @DisplayName("Inicjalizacja stanu — nowy serwis dostaje stan domyślny")
    class Initialization {

        @Test
        @DisplayName("nowy serwis startuje w stanie CLOSED z zerową liczbą błędów")
        void returnsClosedStateForNewService() {
            CircuitBreakerState state = repository.getOrInit("soap-service");

            assertThat(state.state()).isEqualTo(State.CLOSED);
            assertThat(state.failureCount()).isZero();
            assertThat(state.openedAt()).isNull(); // openedAt ma sens tylko gdy OPEN
        }

        @Test
        @DisplayName("kolejne wywołania getOrInit dla tego samego serwisu zwracają ten sam stan")
        void returnsSameStateOnSubsequentCalls() {
            CircuitBreakerState first = repository.getOrInit("soap-service");
            CircuitBreakerState second = repository.getOrInit("soap-service");

            assertThat(first).isEqualTo(second);
        }
    }

    @Nested
    @DisplayName("Zapisywanie i odczytywanie stanu")
    class Persistence {

        @Test
        @DisplayName("zapisany stan jest dostępny przy kolejnym getOrInit")
        void persistsStateAfterSave() {
            CircuitBreakerState openState = new CircuitBreakerState(State.OPEN, 5, Instant.now());

            repository.save("soap-service", openState);

            assertThat(repository.getOrInit("soap-service")).isEqualTo(openState);
        }

        @Test
        @DisplayName("stan jednego serwisu nie wpływa na stan innego serwisu")
        void isolatesStateBetweenServices() {
            repository.save("soap-service", new CircuitBreakerState(State.OPEN, 5, Instant.now()));

            // product-service powinien mieć własny, czysty stan
            CircuitBreakerState other = repository.getOrInit("product-service");

            assertThat(other.state()).isEqualTo(State.CLOSED);
        }
    }
}
