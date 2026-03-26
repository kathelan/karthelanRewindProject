package pl.kathelan.auth.domain.visitor;

import pl.kathelan.auth.domain.state.ApprovedState;
import pl.kathelan.auth.domain.state.CancelledState;
import pl.kathelan.auth.domain.state.ClosedState;
import pl.kathelan.auth.domain.state.ExpiredState;
import pl.kathelan.auth.domain.state.PendingState;
import pl.kathelan.auth.domain.state.RejectedState;

public class AllowedActionsVisitor implements AuthStateVisitor {

    private boolean canCancel = false;

    @Override
    public void visit(PendingState state) {
        canCancel = true;
    }

    @Override
    public void visit(ApprovedState state) {
    }

    @Override
    public void visit(RejectedState state) {
    }

    @Override
    public void visit(CancelledState state) {
    }

    @Override
    public void visit(ExpiredState state) {
    }

    @Override
    public void visit(ClosedState state) {
    }

    public boolean canCancel() {
        return canCancel;
    }
}