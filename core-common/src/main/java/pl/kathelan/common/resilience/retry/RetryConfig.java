package pl.kathelan.common.resilience.retry;

import java.time.Duration;
import java.util.Set;

/**
 * Konfiguracja Retry z exponential backoff.
 *
 * @param maxAttempts    łączna liczba prób (wliczając pierwsze wywołanie)
 * @param initialDelay   czas oczekiwania przed drugą próbą
 * @param multiplier     mnożnik dla kolejnych opóźnień (exponential backoff)
 * @param retryOn        na jakie wyjątki reagujemy — puste = wszystkie RuntimeException
 * @param excludeOn      wyjątki nigdy nie retryowane (domenowe, semantyczne) — mają priorytet nad retryOn
 */
public record RetryConfig(
        int maxAttempts,
        Duration initialDelay,
        double multiplier,
        Set<Class<? extends Exception>> retryOn,
        Set<Class<? extends Exception>> excludeOn
) {
    public RetryConfig(int maxAttempts, Duration initialDelay, double multiplier,
                       Set<Class<? extends Exception>> retryOn) {
        this(maxAttempts, initialDelay, multiplier, retryOn, Set.of());
    }

    public static RetryConfig defaults() {
        return new RetryConfig(3, Duration.ofMillis(100), 2.0, Set.of(), Set.of());
    }

    public boolean shouldRetry(Exception e) {
        if (excludeOn.stream().anyMatch(type -> type.isInstance(e))) {
            return false;
        }
        if (retryOn.isEmpty()) return true;
        return retryOn.stream().anyMatch(type -> type.isInstance(e));
    }
}
