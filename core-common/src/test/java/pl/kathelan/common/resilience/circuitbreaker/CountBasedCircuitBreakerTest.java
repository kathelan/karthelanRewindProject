package pl.kathelan.common.resilience.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pl.kathelan.common.resilience.exception.CircuitOpenException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CountBasedCircuitBreakerTest {

    private static final String SERVICE = "soap-service";
    private static final int THRESHOLD = 3;

    private InMemoryCircuitBreakerStateRepository repository;
    private CircuitBreakerConfig config;
    private CountBasedCircuitBreaker cb;

    @BeforeEach
    void setUp() {
        repository = Mockito.spy(new InMemoryCircuitBreakerStateRepository());
        config = new CircuitBreakerConfig(THRESHOLD, Duration.ofSeconds(10), e -> true);
        cb = new CountBasedCircuitBreaker(SERVICE, config, repository);
    }

    // ===== CLOSED state =====

    @Test
    void shouldExecuteCallWhenClosed() {
        String result = cb.execute(() -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldNotCountSuccessAsFailure() {
        cb.execute(() -> "ok");
        cb.execute(() -> "ok");

        assertThat(repository.getOrInit(SERVICE).failureCount()).isZero();
    }

    @Test
    void shouldNotSaveStateOnSuccessWhenNoFailures() {
        // failureCount == 0 → zbędny save nie powinien nastąpić (boundary: > 0 vs >= 0)
        cb.execute(() -> "ok");

        verify(repository, never()).save(Mockito.eq(SERVICE), Mockito.any());
    }

    @Test
    void shouldCountFailureWhenExceptionThrown() {
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("down"); }))
                .isInstanceOf(RuntimeException.class);

        assertThat(repository.getOrInit(SERVICE).failureCount()).isEqualTo(1);
    }

    @Test
    void shouldResetFailureCountAfterSuccess() {
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException(); }));
        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException(); }));

        cb.execute(() -> "ok");

        assertThat(repository.getOrInit(SERVICE).failureCount()).isZero();
    }

    // ===== CLOSED → OPEN =====

    @Test
    void shouldOpenAfterReachingFailureThreshold() {
        triggerFailures(THRESHOLD);

        assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.OPEN);
    }

    @Test
    void shouldNotOpenBeforeReachingThreshold() {
        triggerFailures(THRESHOLD - 1);

        assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.CLOSED);
    }

    // ===== OPEN state =====

    @Test
    void shouldRejectCallWhenOpen() {
        triggerFailures(THRESHOLD);

        assertThatThrownBy(() -> cb.execute(() -> "ok"))
                .isInstanceOf(CircuitOpenException.class)
                .hasMessageContaining(SERVICE);
    }

    @Test
    void shouldNotCountRejectedCallAsFailure() {
        triggerFailures(THRESHOLD);
        int failureCountAfterOpen = repository.getOrInit(SERVICE).failureCount();

        assertThatThrownBy(() -> cb.execute(() -> "ok")).isInstanceOf(CircuitOpenException.class);

        assertThat(repository.getOrInit(SERVICE).failureCount()).isEqualTo(failureCountAfterOpen);
    }

    // ===== OPEN → HALF_OPEN =====

    @Test
    void shouldTransitionToHalfOpenAfterTimeout() {
        triggerFailures(THRESHOLD);
        forceOpenedAt(Instant.now().minus(Duration.ofSeconds(11)));

        String result = cb.execute(() -> "probe");

        // przeszło bez CircuitOpenException — CB w HALF_OPEN przepuścił wywołanie
        assertThat(result).isEqualTo("probe");
        assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.CLOSED);
    }

    // ===== HALF_OPEN → CLOSED =====

    @Test
    void shouldCloseAfterSuccessfulProbeInHalfOpen() {
        forceState(State.HALF_OPEN);

        String result = cb.execute(() -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.CLOSED);
        assertThat(repository.getOrInit(SERVICE).failureCount()).isZero();
    }

    // ===== HALF_OPEN → OPEN =====

    @Test
    void shouldReopenAfterFailedProbeInHalfOpen() {
        forceState(State.HALF_OPEN);

        assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("still down"); }))
                .isInstanceOf(RuntimeException.class);

        assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.OPEN);
    }

    // ===== FailurePredicate =====

    @Test
    void shouldNotCountFailureWhenPredicateSaysNo() {
        config = new CircuitBreakerConfig(THRESHOLD, Duration.ofSeconds(10),
                e -> !(e instanceof IllegalArgumentException));
        cb = new CountBasedCircuitBreaker(SERVICE, config, repository);

        assertThatThrownBy(() -> cb.execute(() -> { throw new IllegalArgumentException("business error"); }));

        assertThat(repository.getOrInit(SERVICE).failureCount()).isZero();
    }

    // ===== helpers =====

    private void triggerFailures(int count) {
        for (int i = 0; i < count; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("down"); });
            } catch (Exception ignored) {}
        }
    }

    private void forceState(State state) {
        repository.save(SERVICE, new CircuitBreakerState(state, THRESHOLD, Instant.now()));
    }

    private void forceOpenedAt(Instant openedAt) {
        CircuitBreakerState current = repository.getOrInit(SERVICE);
        repository.save(SERVICE, new CircuitBreakerState(current.state(), current.failureCount(), openedAt));
    }
}
