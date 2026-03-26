package pl.kathelan.auth.domain;

import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.domain.state.ApprovedState;
import pl.kathelan.auth.domain.state.AuthProcessState;
import pl.kathelan.auth.domain.state.CancelledState;
import pl.kathelan.auth.domain.state.ClosedState;
import pl.kathelan.auth.domain.state.ExpiredState;
import pl.kathelan.auth.domain.state.PendingState;
import pl.kathelan.auth.domain.state.RejectedState;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuthProcess(
        UUID id,
        String userId,
        AuthMethod authMethod,
        String deliveryId,
        AuthProcessState state,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AuthProcess create(String userId, AuthMethod authMethod) {
        LocalDateTime now = LocalDateTime.now();
        return new AuthProcess(UUID.randomUUID(), userId, authMethod, null, new PendingState(), now, now);
    }

    public ProcessState processState() {
        return state.processState();
    }

    public boolean isTerminal() {
        return state.isTerminal();
    }

    public AuthProcess approve() {
        return transition(new ApprovedState());
    }

    public AuthProcess reject() {
        return transition(new RejectedState());
    }

    public AuthProcess cancel() {
        return transition(new CancelledState());
    }

    public AuthProcess expire() {
        return transition(new ExpiredState());
    }

    public AuthProcess close() {
        return transition(new ClosedState());
    }

    public AuthProcess withDeliveryId(String deliveryId) {
        return new AuthProcess(id, userId, authMethod, deliveryId, state, createdAt, updatedAt);
    }

    private AuthProcess transition(AuthProcessState newState) {
        state.validateTransition(newState.processState());
        return new AuthProcess(id, userId, authMethod, deliveryId, newState, createdAt, LocalDateTime.now());
    }
}