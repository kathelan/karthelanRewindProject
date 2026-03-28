package pl.kathelan.auth.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterRegistry {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID processId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(processId, emitter);
        emitter.onCompletion(() -> emitters.remove(processId));
        emitter.onTimeout(() -> emitters.remove(processId));
        emitter.onError(e -> emitters.remove(processId));
        return emitter;
    }

    public void send(UUID processId, ProcessStreamEvent event) {
        SseEmitter emitter = emitters.get(processId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("state").data(event));
        } catch (IOException e) {
            log.warn("Failed to send SSE event for process {}: {}", processId, e.getMessage());
            emitters.remove(processId);
        }
    }

    public void complete(UUID processId) {
        SseEmitter emitter = emitters.remove(processId);
        if (emitter != null) emitter.complete();
    }
}