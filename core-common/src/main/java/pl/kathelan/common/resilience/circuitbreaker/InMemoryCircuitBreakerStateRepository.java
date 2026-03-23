package pl.kathelan.common.resilience.circuitbreaker;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCircuitBreakerStateRepository implements CircuitBreakerStateRepository {

    private final ConcurrentHashMap<String, CircuitBreakerState> store = new ConcurrentHashMap<>();

    @Override
    public CircuitBreakerState getOrInit(String serviceName) {
        return store.computeIfAbsent(serviceName, key -> CircuitBreakerState.initialClosed());
    }

    @Override
    public void save(String serviceName, CircuitBreakerState state) {
        store.put(serviceName, state);
    }
}
