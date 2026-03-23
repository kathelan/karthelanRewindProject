package pl.kathelan.common.resilience.circuitbreaker;

import java.util.function.Supplier;

public interface CircuitBreaker {

    /**
     * Wykonuje wywołanie przez CB.
     * Rzuca CircuitOpenException gdy CB jest OPEN.
     * Rzuca oryginalny wyjątek gdy wywołanie się nie powiodło.
     */
    <T> T execute(Supplier<T> call);
}
