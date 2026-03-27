package pl.kathelan.auth.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthAuditListener {

    @EventListener
    public void onStateChanged(AuthProcessStateChangedEvent event) {
        log.info("AUDIT [{}] processId={} userId={}", event.newState(), event.processId(), event.userId());
    }
}