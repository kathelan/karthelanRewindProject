package pl.kathelan.auth.api.exception;

import pl.kathelan.auth.api.dto.ProcessState;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(ProcessState from, ProcessState to) {
        super("Cannot transition from " + from + " to " + to);
    }
}