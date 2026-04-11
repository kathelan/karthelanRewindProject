package pl.kathelan.common.resilience.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryExecutorTest {

    private final RetryExecutor executor = new RetryExecutor();

    /** Subklasa do śledzenia wywołań sleep bez realnego opóźnienia. */
    private static class TrackingRetryExecutor extends RetryExecutor {
        final List<Long> sleepDurations = new ArrayList<>();

        @Override
        protected void sleep(long ms) {
            sleepDurations.add(ms);
        }
    }

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

    // ===== exponential backoff — tracking (logika wywołań) =====

    @Test
    void shouldApplyExponentialBackoff() {
        TrackingRetryExecutor tracking = new TrackingRetryExecutor();
        RetryConfig config = new RetryConfig(3, Duration.ofMillis(50), 2.0, Set.of());

        assertThatThrownBy(() -> tracking.execute(() -> { throw new RuntimeException(); }, config));

        // 3 próby → 2 sny: 50ms, 100ms
        assertThat(tracking.sleepDurations).containsExactly(50L, 100L);
    }

    @Test
    void shouldSleepOnlyBetweenAttemptsNotAfterLast() {
        TrackingRetryExecutor tracking = new TrackingRetryExecutor();
        RetryConfig config = new RetryConfig(4, Duration.ofMillis(10), 1.0, Set.of());

        assertThatThrownBy(() -> tracking.execute(() -> { throw new RuntimeException(); }, config));

        // 4 próby → dokładnie 3 sny (nie 4)
        assertThat(tracking.sleepDurations).hasSize(3);
    }

    @Test
    void shouldPassZeroDurationToSleepWithoutSleeping() {
        TrackingRetryExecutor tracking = new TrackingRetryExecutor();
        RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0, Set.of());

        assertThatThrownBy(() -> tracking.execute(() -> { throw new RuntimeException(); }, config));

        assertThat(tracking.sleepDurations).hasSize(2).containsOnly(0L);
    }

    // ===== exponential backoff — timing (realny sleep, weryfikuje sleep body) =====

    @Test
    void shouldActuallyDelayBetweenRetries() {
        // Prawdziwy executor — weryfikuje że Thread.sleep faktycznie blokuje (nie tylko rejestruje)
        // Zabija mutację: negacja warunku w sleep (ms > 0 zamiast ms <= 0 → pomija real sleep)
        RetryConfig config = new RetryConfig(3, Duration.ofMillis(50), 2.0, Set.of());

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> executor.execute(() -> { throw new RuntimeException(); }, config));
        long elapsed = System.currentTimeMillis() - start;

        // 50ms + 100ms = 150ms — jeśli sleep pominięty, elapsed << 150ms
        assertThat(elapsed).isGreaterThanOrEqualTo(150);
    }
}
