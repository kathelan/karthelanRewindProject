package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;

public final class RejectedState extends TerminalAuthProcessState {

    @Override
    public ProcessState processState() {
        return ProcessState.REJECTED;
    }
}