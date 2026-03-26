package pl.kathelan.auth.domain.visitor;

import org.junit.jupiter.api.Test;
import pl.kathelan.auth.domain.state.ApprovedState;
import pl.kathelan.auth.domain.state.CancelledState;
import pl.kathelan.auth.domain.state.ClosedState;
import pl.kathelan.auth.domain.state.ExpiredState;
import pl.kathelan.auth.domain.state.PendingState;
import pl.kathelan.auth.domain.state.RejectedState;

import static org.assertj.core.api.Assertions.assertThat;

class AllowedActionsVisitorTest {

    @Test
    void pendingAllowsCancel() {
        AllowedActionsVisitor visitor = new AllowedActionsVisitor();
        new PendingState().accept(visitor);
        assertThat(visitor.canCancel()).isTrue();
    }

    @Test
    void approvedDoesNotAllowCancel() {
        AllowedActionsVisitor visitor = new AllowedActionsVisitor();
        new ApprovedState().accept(visitor);
        assertThat(visitor.canCancel()).isFalse();
    }

    @Test
    void rejectedDoesNotAllowCancel() {
        AllowedActionsVisitor visitor = new AllowedActionsVisitor();
        new RejectedState().accept(visitor);
        assertThat(visitor.canCancel()).isFalse();
    }

    @Test
    void cancelledDoesNotAllowCancel() {
        AllowedActionsVisitor visitor = new AllowedActionsVisitor();
        new CancelledState().accept(visitor);
        assertThat(visitor.canCancel()).isFalse();
    }

    @Test
    void expiredDoesNotAllowCancel() {
        AllowedActionsVisitor visitor = new AllowedActionsVisitor();
        new ExpiredState().accept(visitor);
        assertThat(visitor.canCancel()).isFalse();
    }

    @Test
    void closedDoesNotAllowCancel() {
        AllowedActionsVisitor visitor = new AllowedActionsVisitor();
        new ClosedState().accept(visitor);
        assertThat(visitor.canCancel()).isFalse();
    }

    @Test
    void visitorIsReusableAcrossAcceptCalls() {
        AllowedActionsVisitor visitor = new AllowedActionsVisitor();
        new PendingState().accept(visitor);
        assertThat(visitor.canCancel()).isTrue();

        AllowedActionsVisitor visitor2 = new AllowedActionsVisitor();
        new ApprovedState().accept(visitor2);
        assertThat(visitor2.canCancel()).isFalse();
    }
}