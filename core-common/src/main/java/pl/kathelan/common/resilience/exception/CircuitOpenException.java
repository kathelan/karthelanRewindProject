package pl.kathelan.common.resilience.exception;

public class CircuitOpenException extends RuntimeException {

    public CircuitOpenException(String serviceName) {
        super("Circuit breaker is OPEN for service: " + serviceName);
    }
}
