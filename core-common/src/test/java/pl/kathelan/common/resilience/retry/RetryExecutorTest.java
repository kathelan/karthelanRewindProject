package pl.kathelan.common.resilience.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryExecutorTest {

    private final RetryExecutor executor = new RetryExecutor();

    // ===== sukces =====

    @Test
    void shouldReturnResultOnFirstSuccess() {
        String result = executor.execute(() -> "ok", RetryConfig.defaults());

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldSucceedAfterRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0, Set.of());

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) throw new RuntimeException("not yet");
            return "ok";
        }, config);

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    // ===== wyczerpanie prób =====

    @Test
    void shouldThrowAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0, Set.of());

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("always fails");
        }, config))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("always fails");

        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldAttemptExactlyMaxAttemptsTimes() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryConfig config = new RetryConfig(5, Duration.ZERO, 1.0, Set.of());

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException();
        }, config));

        assertThat(attempts.get()).isEqualTo(5);
    }

    // ===== retryOn =====

    @Test
    void shouldNotRetryWhenExceptionNotInRetryOn() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0,
                Set.of(IllegalStateException.class));

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("not retryable");
        }, config))
                .isInstanceOf(RuntimeException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldRetryWhenExceptionMatchesRetryOn() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0,
                Set.of(RuntimeException.class));

        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException("retryable");
        }, config));

        assertThat(attempts.get()).isEqualTo(3);
    }

    // ===== exponential backoff =====

    @Test
    void shouldApplyExponentialBackoff() {
        AtomicInteger attempts = new AtomicInteger(0);
        RetryConfig config = new RetryConfig(3, Duration.ofMillis(50), 2.0, Set.of());

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new RuntimeException();
        }, config));
        long elapsed = System.currentTimeMillis() - start;

        // 50ms + 100ms = 150ms minimalny oczekiwany czas
        assertThat(elapsed).isGreaterThanOrEqualTo(150);
    }

    @Test
    void shouldSleepExactlyBetweenAttemptsNotAfterLast() {
        // 3 próby, stały delay 100ms (multiplier=1.0):
        // poprawnie:               2 sny = ~200ms
        // mutant (sleep po ostatniej też): 3 sny = ~300ms  → fail < 280
        // mutant (sleep tylko na ostatniej): 1 sen = ~100ms → fail >= 200
        RetryConfig config = new RetryConfig(3, Duration.ofMillis(100), 1.0, Set.of());

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> executor.execute(() -> { throw new RuntimeException(); }, config));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(200)
                           .isLessThan(280);
    }
}
