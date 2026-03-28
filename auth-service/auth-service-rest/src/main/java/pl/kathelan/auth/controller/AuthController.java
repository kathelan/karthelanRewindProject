package pl.kathelan.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pl.kathelan.auth.api.dto.CapabilitiesResponse;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.InitProcessResponse;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.api.dto.ProcessStatusResponse;
import pl.kathelan.auth.service.AuthProcessService;
import pl.kathelan.auth.sse.ProcessStreamEvent;
import pl.kathelan.auth.sse.SseEmitterRegistry;
import pl.kathelan.common.api.ApiResponse;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthProcessService service;
    private final SseEmitterRegistry sseRegistry;

    @GetMapping("/auth/capabilities/{userId}")
    public ResponseEntity<ApiResponse<CapabilitiesResponse>> getCapabilities(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success(service.getCapabilities(userId)));
    }

    @PostMapping("/process/init")
    public ResponseEntity<ApiResponse<InitProcessResponse>> init(@Valid @RequestBody InitProcessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.initProcess(request)));
    }

    @GetMapping(value = "/process/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID id) {
        ProcessStatusResponse current = service.getStatus(id);
        SseEmitter emitter = sseRegistry.register(id);
        try {
            emitter.send(SseEmitter.event().name("state").data(ProcessStreamEvent.of(current.state())));
            if (current.state() != ProcessState.PENDING) {
                sseRegistry.complete(id);
            }
        } catch (IOException e) {
            sseRegistry.complete(id);
        }
        return emitter;
    }

    @PatchMapping("/process/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        service.cancel(id);
    }
}