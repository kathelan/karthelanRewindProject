package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.domain.visitor.AuthStateVisitor;

public final class ExpiredState extends TerminalAuthProcessState {

    @Override
    public ProcessState processState() {
        return ProcessState.EXPIRED;
    }

    @Override
    public void accept(AuthStateVisitor visitor) {
        visitor.visit(this);
    }
}