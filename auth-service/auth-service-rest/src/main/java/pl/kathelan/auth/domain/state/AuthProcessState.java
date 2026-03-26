package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.domain.visitor.AuthStateVisitor;

public interface AuthProcessState {
    ProcessState processState();
    boolean isTerminal();
    void validateTransition(ProcessState target);
    void accept(AuthStateVisitor visitor);
}