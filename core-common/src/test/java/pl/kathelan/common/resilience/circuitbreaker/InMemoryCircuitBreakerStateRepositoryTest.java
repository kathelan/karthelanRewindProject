package pl.kathelan.common.resilience.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCircuitBreakerStateRepositoryTest {

    private InMemoryCircuitBreakerStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCircuitBreakerStateRepository();
    }

    @Test
    void getOrInit_shouldReturnClosedStateForNewService() {
        CircuitBreakerState state = repository.getOrInit("soap-service");

        assertThat(state.state()).isEqualTo(State.CLOSED);
        assertThat(state.failureCount()).isZero();
        assertThat(state.openedAt()).isNull();
    }

    @Test
    void getOrInit_shouldReturnSameStateOnSubsequentCalls() {
        CircuitBreakerState first = repository.getOrInit("soap-service");
        CircuitBreakerState second = repository.getOrInit("soap-service");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void save_shouldPersistNewState() {
        CircuitBreakerState openState = new CircuitBreakerState(State.OPEN, 5, Instant.now());

        repository.save("soap-service", openState);

        assertThat(repository.getOrInit("soap-service")).isEqualTo(openState);
    }

    @Test
    void shouldIsolateStatePerService() {
        CircuitBreakerState openState = new CircuitBreakerState(State.OPEN, 5, Instant.now());
        repository.save("soap-service", openState);

        CircuitBreakerState other = repository.getOrInit("product-service");

        assertThat(other.state()).isEqualTo(State.CLOSED);
    }
}
