package pl.kathelan.common.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerRegistry;
import pl.kathelan.common.resilience.circuitbreaker.CountBasedCircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.exception.CircuitOpenException;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientCallerTest {

    private static final String SERVICE = "test-service";
    private static final int CB_THRESHOLD = 3;

    private InMemoryCircuitBreakerStateRepository repository;
    private CountBasedCircuitBreaker circuitBreaker;
    private ResilientCaller caller;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCircuitBreakerStateRepository();
        CircuitBreakerConfig cbConfig = new CircuitBreakerConfig(CB_THRESHOLD, Duration.ofSeconds(30), e -> true);
        circuitBreaker = new CountBasedCircuitBreaker(SERVICE, cbConfig, repository);
        RetryConfig retryConfig = new RetryConfig(3, Duration.ZERO, 1.0, Set.of());
        caller = new ResilientCaller(circuitBreaker, new RetryExecutor(), retryConfig);
    }

    @Test
    void shouldReturnResultWhenSupplierSucceeds() {
        String result = caller.call(() -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldRetryAndSucceedBeforeExhaustingAttempts() {
        AtomicInteger calls = new AtomicInteger(0);

        String result = caller.call(() -> {
            if (calls.incrementAndGet() < 3) throw new RuntimeException("transient");
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void shouldCountOnlyOneFailureInCircuitBreakerAfterRetryExhausted() {
        // Retry wyczerpuje 3 próby — CB liczy 1 failure, nie 3
        assertThatThrownBy(() -> caller.call(() -> { throw new RuntimeException("down"); }));

        assertThat(repository.getOrInit(SERVICE).failureCount()).isEqualTo(1);
    }

    @Test
    void shouldOpenCircuitBreakerOnlyAfterFinalRetryFailure() {
        // Dopiero po CB_THRESHOLD wyczerpanych retry-seriach CB się otwiera
        for (int i = 0; i < CB_THRESHOLD - 1; i++) {
            int attempt = i;
            assertThatThrownBy(() -> caller.call(() -> { throw new RuntimeException("down " + attempt); }));
        }
        assertThat(repository.getOrInit(SERVICE).state())
                .isEqualTo(pl.kathelan.common.resilience.circuitbreaker.State.CLOSED);

        assertThatThrownBy(() -> caller.call(() -> { throw new RuntimeException("final"); }));

        assertThat(repository.getOrInit(SERVICE).state())
                .isEqualTo(pl.kathelan.common.resilience.circuitbreaker.State.OPEN);
    }

    @Test
    void shouldRejectCallImmediatelyWhenCircuitBreakerIsOpen() {
        // Otwieramy CB
        for (int i = 0; i < CB_THRESHOLD; i++) {
            assertThatThrownBy(() -> caller.call(() -> { throw new RuntimeException(); }));
        }

        AtomicInteger supplierCalls = new AtomicInteger(0);
        assertThatThrownBy(() -> caller.call(() -> {
            supplierCalls.incrementAndGet();
            return "never";
        })).isInstanceOf(CircuitOpenException.class);

        assertThat(supplierCalls.get()).isZero();
    }
}
