package pl.kathelan.common.resilience.circuitbreaker;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rejestr Circuit Breakerów — jeden CB per serwis.
 * Thread-safe: ConcurrentHashMap + computeIfAbsent.
 */
@Slf4j
public class CircuitBreakerRegistry {

    private final ConcurrentHashMap<String, CircuitBreaker> registry = new ConcurrentHashMap<>();
    private final CircuitBreakerStateRepository stateRepository;

    public CircuitBreakerRegistry(CircuitBreakerStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    public CircuitBreaker getOrCreate(String serviceName, CircuitBreakerConfig config) {
        return registry.computeIfAbsent(serviceName, key -> {
            log.info("CircuitBreaker [{}] created", serviceName);
            return new CountBasedCircuitBreaker(key, config, stateRepository);
        });
    }
}
