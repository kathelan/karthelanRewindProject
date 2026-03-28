package pl.kathelan.auth.api.exception;

import pl.kathelan.auth.api.dto.ProcessState;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(ProcessState from, ProcessState to) {
        super("Cannot transition from " + from + " to " + to);
    }

    public InvalidStateTransitionException(java.util.UUID processId, ProcessState from, ProcessState to) {
        super("Process " + processId + ": cannot transition from " + from + " to " + to);
    }
}