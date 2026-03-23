package pl.kathelan.common.resilience.circuitbreaker;

import lombok.extern.slf4j.Slf4j;
import pl.kathelan.common.resilience.exception.CircuitOpenException;

import java.time.Instant;
import java.util.function.Supplier;

@Slf4j
public class CountBasedCircuitBreaker implements CircuitBreaker {

    private final String serviceName;
    private final CircuitBreakerConfig config;
    private final CircuitBreakerStateRepository repository;

    public CountBasedCircuitBreaker(String serviceName,
                                    CircuitBreakerConfig config,
                                    CircuitBreakerStateRepository repository) {
        this.serviceName = serviceName;
        this.config = config;
        this.repository = repository;
    }

    @Override
    public <T> T execute(Supplier<T> call) {
        CircuitBreakerState state = repository.getOrInit(serviceName);

        return switch (state.state()) {
            case CLOSED -> executeWhenClosed(call, state);
            case OPEN -> handleOpen(call, state);
            case HALF_OPEN -> executeWhenHalfOpen(call, state);
        };
    }

    private <T> T executeWhenClosed(Supplier<T> call, CircuitBreakerState state) {
        log.debug("CircuitBreaker [{}] CLOSED — executing call", serviceName);
        try {
            T result = call.get();
            if (state.failureCount() > 0) {
                repository.save(serviceName, CircuitBreakerState.initialClosed());
            }
            return result;
        } catch (Exception e) {
            return handleFailure(e, state);
        }
    }

    private <T> T handleOpen(Supplier<T> call, CircuitBreakerState state) {
        if (timeoutElapsed(state)) {
            log.info("CircuitBreaker [{}] OPEN → HALF_OPEN (timeout elapsed)", serviceName);
            CircuitBreakerState halfOpen = new CircuitBreakerState(State.HALF_OPEN, state.failureCount(), state.openedAt());
            repository.save(serviceName, halfOpen);
            return executeWhenHalfOpen(call, halfOpen);
        }
        log.error("CircuitBreaker [{}] is OPEN — call rejected", serviceName);
        throw new CircuitOpenException(serviceName);
    }

    private <T> T executeWhenHalfOpen(Supplier<T> call, CircuitBreakerState state) {
        log.debug("CircuitBreaker [{}] HALF_OPEN — executing probe call", serviceName);
        try {
            T result = call.get();
            log.info("CircuitBreaker [{}] HALF_OPEN → CLOSED (probe succeeded)", serviceName);
            repository.save(serviceName, CircuitBreakerState.initialClosed());
            return result;
        } catch (Exception e) {
            log.info("CircuitBreaker [{}] HALF_OPEN → OPEN (probe failed)", serviceName);
            repository.save(serviceName, new CircuitBreakerState(State.OPEN, state.failureCount(), Instant.now()));
            throw rethrow(e);
        }
    }

    private <T> T handleFailure(Exception e, CircuitBreakerState state) {
        if (!config.failurePredicate().isFailure(e)) {
            log.debug("CircuitBreaker [{}] exception not counted as failure: {}", serviceName, e.getClass().getSimpleName());
            throw rethrow(e);
        }

        int newCount = state.failureCount() + 1;
        log.warn("CircuitBreaker [{}] failure recorded ({}/{})", serviceName, newCount, config.failureThreshold());

        if (newCount >= config.failureThreshold()) {
            log.info("CircuitBreaker [{}] CLOSED → OPEN after {} failures", serviceName, newCount);
            repository.save(serviceName, new CircuitBreakerState(State.OPEN, newCount, Instant.now()));
        } else {
            repository.save(serviceName, new CircuitBreakerState(State.CLOSED, newCount, null));
        }

        throw rethrow(e);
    }

    private boolean timeoutElapsed(CircuitBreakerState state) {
        return state.openedAt() != null &&
                Instant.now().isAfter(state.openedAt().plus(config.openTimeout()));
    }

    @SuppressWarnings("unchecked")
    private <E extends RuntimeException> RuntimeException rethrow(Exception e) {
        throw (E) e;
    }
}
