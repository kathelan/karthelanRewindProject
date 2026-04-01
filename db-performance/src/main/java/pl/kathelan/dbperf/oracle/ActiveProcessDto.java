package pl.kathelan.dbperf.oracle;

import java.time.LocalDateTime;

public record ActiveProcessDto(
        long id,
        String userId,
        String authMethod,
        String status,
        LocalDateTime createdAt
) {}
