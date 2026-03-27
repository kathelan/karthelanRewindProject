package pl.kathelan.auth.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.auth.api.dto.ProcessState;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;

class AuthAuditListenerTest {

    private AuthAuditListener listener;

    @BeforeEach
    void setUp() {
        listener = new AuthAuditListener();
    }

    @Test
    void onStateChanged_approved_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
                listener.onStateChanged(new AuthProcessStateChangedEvent(UUID.randomUUID(), "user1", ProcessState.APPROVED)));
    }

    @Test
    void onStateChanged_expired_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
                listener.onStateChanged(new AuthProcessStateChangedEvent(UUID.randomUUID(), "user1", ProcessState.EXPIRED)));
    }

    @Test
    void onStateChanged_cancelled_doesNotThrow() {
        assertThatNoException().isThrownBy(() ->
                listener.onStateChanged(new AuthProcessStateChangedEvent(UUID.randomUUID(), "user1", ProcessState.CANCELLED)));
    }
}