package pl.kathelan.common.resilience.circuitbreaker;

/**
 * Repozytorium stanu Circuit Breakera.
 * Teraz: InMemory (ConcurrentHashMap).
 * Przyszłość: JPA (tabela circuit_breaker_state) — podmiana implementacji bez zmiany logiki CB.
 */
public interface CircuitBreakerStateRepository {

    CircuitBreakerState getOrInit(String serviceName);

    void save(String serviceName, CircuitBreakerState state);
}
