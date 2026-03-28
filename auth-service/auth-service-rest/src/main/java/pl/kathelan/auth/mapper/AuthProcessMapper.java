package pl.kathelan.auth.mapper;

import pl.kathelan.auth.api.dto.ProcessStatusResponse;
import pl.kathelan.auth.domain.AuthProcess;

public final class AuthProcessMapper {

    private AuthProcessMapper() {}

    public static ProcessStatusResponse toResponse(AuthProcess p) {
        return new ProcessStatusResponse(
                p.id().toString(),
                p.userId(),
                p.processState(),
                p.authMethod(),
                p.createdAt(),
                p.updatedAt(),
                p.expiresAt()
        );
    }
}