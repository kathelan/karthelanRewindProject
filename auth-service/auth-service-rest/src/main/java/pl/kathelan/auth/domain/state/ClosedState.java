package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;

public final class ClosedState extends TerminalAuthProcessState {

    @Override
    public ProcessState processState() {
        return ProcessState.CLOSED;
    }
}