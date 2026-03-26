package pl.kathelan.auth.api.dto;

import java.time.LocalDateTime;

public record ProcessStatusResponse(
        String processId,
        String userId,
        ProcessState state,
        AuthMethod authMethod,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}