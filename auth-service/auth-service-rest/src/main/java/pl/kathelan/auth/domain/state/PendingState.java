package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.api.exception.InvalidStateTransitionException;

import java.util.Set;

public final class PendingState implements AuthProcessState {

    private static final Set<ProcessState> ALLOWED = Set.of(
            ProcessState.APPROVED,
            ProcessState.REJECTED,
            ProcessState.CANCELLED,
            ProcessState.EXPIRED,
            ProcessState.CLOSED
    );

    @Override
    public ProcessState processState() {
        return ProcessState.PENDING;
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public void validateTransition(ProcessState target) {
        if (!ALLOWED.contains(target)) {
            throw new InvalidStateTransitionException(ProcessState.PENDING, target);
        }
    }

}
