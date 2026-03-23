package pl.kathelan.common.resilience.circuitbreaker;

import java.time.Duration;

/**
 * Konfiguracja Circuit Breakera.
 *
 * @param failureThreshold  ile kolejnych błędów otwiera CB (np. 5)
 * @param openTimeout       jak długo CB siedzi w OPEN zanim przejdzie do HALF_OPEN (np. 10s)
 * @param failurePredicate  co liczymy jako failure — decyduje caller, nie CB
 */
public record CircuitBreakerConfig(
        int failureThreshold,
        Duration openTimeout,
        FailurePredicate failurePredicate
) {
    public static CircuitBreakerConfig defaults() {
        return new CircuitBreakerConfig(5, Duration.ofSeconds(10), e -> true);
    }
}
