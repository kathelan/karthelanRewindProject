package pl.kathelan.auth.event;

import pl.kathelan.auth.api.dto.ProcessState;

import java.util.UUID;

public record AuthProcessStateChangedEvent(UUID processId, String userId, ProcessState newState) {}