package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;

public final class ApprovedState extends TerminalAuthProcessState {

    @Override
    public ProcessState processState() {
        return ProcessState.APPROVED;
    }
}