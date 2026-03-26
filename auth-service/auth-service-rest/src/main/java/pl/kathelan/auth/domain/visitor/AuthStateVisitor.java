package pl.kathelan.auth.domain.visitor;

import pl.kathelan.auth.domain.state.ApprovedState;
import pl.kathelan.auth.domain.state.CancelledState;
import pl.kathelan.auth.domain.state.ClosedState;
import pl.kathelan.auth.domain.state.ExpiredState;
import pl.kathelan.auth.domain.state.PendingState;
import pl.kathelan.auth.domain.state.RejectedState;

public interface AuthStateVisitor {
    void visit(PendingState state);
    void visit(ApprovedState state);
    void visit(RejectedState state);
    void visit(CancelledState state);
    void visit(ExpiredState state);
    void visit(ClosedState state);
}