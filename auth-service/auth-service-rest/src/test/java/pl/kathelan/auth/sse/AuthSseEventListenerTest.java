package pl.kathelan.auth.sse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.event.AuthProcessStateChangedEvent;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthSseEventListenerTest {

    @Mock
    private SseEmitterRegistry registry;

    @InjectMocks
    private AuthSseEventListener listener;

    @Test
    void onStateChanged_sendsEventForAnyState() {
        UUID processId = UUID.randomUUID();
        listener.onStateChanged(new AuthProcessStateChangedEvent(processId, "user1", ProcessState.APPROVED));

        verify(registry).send(processId, ProcessStreamEvent.of(ProcessState.APPROVED));
    }

    @Test
    void onStateChanged_completesEmitterOnTerminalState() {
        UUID processId = UUID.randomUUID();
        listener.onStateChanged(new AuthProcessStateChangedEvent(processId, "user1", ProcessState.APPROVED));

        verify(registry).complete(processId);
    }

    @Test
    void onStateChanged_doesNotCompleteEmitterForPendingState() {
        UUID processId = UUID.randomUUID();
        listener.onStateChanged(new AuthProcessStateChangedEvent(processId, "user1", ProcessState.PENDING));

        verify(registry, never()).complete(processId);
    }

    @Test
    void onStateChanged_completesOnRejected() {
        UUID processId = UUID.randomUUID();
        listener.onStateChanged(new AuthProcessStateChangedEvent(processId, "user1", ProcessState.REJECTED));

        verify(registry).complete(processId);
    }

    @Test
    void onStateChanged_completesOnCancelled() {
        UUID processId = UUID.randomUUID();
        listener.onStateChanged(new AuthProcessStateChangedEvent(processId, "user1", ProcessState.CANCELLED));

        verify(registry).complete(processId);
    }

    @Test
    void onStateChanged_completesOnExpired() {
        UUID processId = UUID.randomUUID();
        listener.onStateChanged(new AuthProcessStateChangedEvent(processId, "user1", ProcessState.EXPIRED));

        verify(registry).complete(processId);
    }
}