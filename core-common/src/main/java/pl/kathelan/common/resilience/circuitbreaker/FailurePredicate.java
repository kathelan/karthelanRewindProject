package pl.kathelan.common.resilience.circuitbreaker;

@FunctionalInterface
public interface FailurePredicate {
    boolean isFailure(Exception e);
}
