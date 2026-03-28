package pl.kathelan.auth.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.event.AuthProcessStateChangedEvent;

@Component
@RequiredArgsConstructor
public class AuthSseEventListener {

    private final SseEmitterRegistry registry;

    @EventListener
    public void onStateChanged(AuthProcessStateChangedEvent event) {
        registry.send(event.processId(), ProcessStreamEvent.of(event.newState()));
        if (event.newState() != ProcessState.PENDING) {
            registry.complete(event.processId());
        }
    }
}
