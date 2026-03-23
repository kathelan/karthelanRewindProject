package pl.kathelan.common.resilience.circuitbreaker;

public enum State {
    CLOSED,
    OPEN,
    HALF_OPEN
}
