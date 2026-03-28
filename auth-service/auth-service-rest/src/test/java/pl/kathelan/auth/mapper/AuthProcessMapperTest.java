package pl.kathelan.auth.mapper;

import org.junit.jupiter.api.Test;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.api.dto.ProcessStatusResponse;
import pl.kathelan.auth.domain.AuthProcess;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuthProcessMapperTest {

    @Test
    void toResponse_mapsAllFields() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH)
                .assignDelivery("delivery-1", LocalDateTime.of(2025, 6, 15, 12, 0, 0));

        ProcessStatusResponse response = AuthProcessMapper.toResponse(process);

        assertThat(response.processId()).isEqualTo(process.id().toString());
        assertThat(response.userId()).isEqualTo("user1");
        assertThat(response.state()).isEqualTo(ProcessState.PENDING);
        assertThat(response.authMethod()).isEqualTo(AuthMethod.PUSH);
        assertThat(response.createdAt()).isEqualTo(process.createdAt());
        assertThat(response.updatedAt()).isEqualTo(process.updatedAt());
        assertThat(response.expiresAt()).isEqualTo(LocalDateTime.of(2025, 6, 15, 12, 0, 0));
    }

    @Test
    void toResponse_handlesNullExpiresAt() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);

        ProcessStatusResponse response = AuthProcessMapper.toResponse(process);

        assertThat(response.expiresAt()).isNull();
    }
}