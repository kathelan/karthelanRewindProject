package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.api.exception.InvalidStateTransitionException;

abstract class TerminalAuthProcessState implements AuthProcessState {

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public void validateTransition(ProcessState target) {
        throw new InvalidStateTransitionException(processState(), target);
    }
}