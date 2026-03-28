package pl.kathelan.auth.api.dto;

import java.time.LocalDateTime;

public record InitProcessResponse(
        String processId,
        LocalDateTime expiresAt
) {}