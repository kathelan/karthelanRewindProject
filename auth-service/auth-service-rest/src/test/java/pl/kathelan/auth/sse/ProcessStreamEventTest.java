package pl.kathelan.auth.sse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.kathelan.auth.api.dto.ProcessState;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessStreamEventTest {

    @ParameterizedTest
    @EnumSource(value = ProcessState.class, names = "PENDING")
    void of_pendingState_hasCancel(ProcessState state) {
        ProcessStreamEvent event = ProcessStreamEvent.of(state);
        assertThat(event.state()).isEqualTo(state);
        assertThat(event.allowedActions()).containsExactly("CANCEL");
    }

    @ParameterizedTest
    @EnumSource(value = ProcessState.class, names = {"APPROVED", "REJECTED", "EXPIRED", "CANCELLED", "CLOSED"})
    void of_terminalState_hasNoActions(ProcessState state) {
        ProcessStreamEvent event = ProcessStreamEvent.of(state);
        assertThat(event.state()).isEqualTo(state);
        assertThat(event.allowedActions()).isEmpty();
    }
}
