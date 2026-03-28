package pl.kathelan.auth.domain.state;

import pl.kathelan.auth.api.dto.ProcessState;

public interface AuthProcessState {
    ProcessState processState();
    boolean isTerminal();
    void validateTransition(ProcessState target);
}