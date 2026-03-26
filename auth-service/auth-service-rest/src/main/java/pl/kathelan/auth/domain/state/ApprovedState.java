package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.domain.visitor.AuthStateVisitor;

public final class ApprovedState extends TerminalAuthProcessState {

    @Override
    public ProcessState processState() {
        return ProcessState.APPROVED;
    }

    @Override
    public void accept(AuthStateVisitor visitor) {
        visitor.visit(this);
    }
}