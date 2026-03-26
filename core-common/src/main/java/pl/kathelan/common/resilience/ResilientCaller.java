package pl.kathelan.common.resilience;

import pl.kathelan.common.resilience.circuitbreaker.CircuitBreaker;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;

import java.util.function.Supplier;

public class ResilientCaller {

    private final CircuitBreaker circuitBreaker;
    private final RetryExecutor retryExecutor;
    private final RetryConfig retryConfig;

    public ResilientCaller(CircuitBreaker circuitBreaker, RetryExecutor retryExecutor, RetryConfig retryConfig) {
        this.circuitBreaker = circuitBreaker;
        this.retryExecutor = retryExecutor;
        this.retryConfig = retryConfig;
    }

    // CB wraps Retry: transient errors handled by Retry don't prematurely open CB.
    // CB counts 1 failure only when Retry exhausts all attempts.
    public <T> T call(Supplier<T> supplier) {
        return circuitBreaker.execute(
                () -> retryExecutor.execute(supplier, retryConfig)
        );
    }
}