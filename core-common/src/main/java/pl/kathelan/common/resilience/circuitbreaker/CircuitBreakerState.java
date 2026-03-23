package pl.kathelan.common.resilience.circuitbreaker;

import java.time.Instant;

/**
 * Niemutowalny snapshot stanu CB.
 * Repozytorium zawsze zastępuje cały rekord — brak partial update.
 */
public record CircuitBreakerState(
        State state,
        int failureCount,
        Instant openedAt   // null gdy CLOSED
) {
    public static CircuitBreakerState initialClosed() {
        return new CircuitBreakerState(State.CLOSED, 0, null);
    }
}
