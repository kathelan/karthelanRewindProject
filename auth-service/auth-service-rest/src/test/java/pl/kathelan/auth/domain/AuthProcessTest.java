package pl.kathelan.auth.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.api.exception.InvalidStateTransitionException;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthProcessTest {

    @Test
    void newProcessIsInPendingState() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        assertThat(process.processState()).isEqualTo(ProcessState.PENDING);
    }

    @Test
    void newProcessIsNotTerminal() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        assertThat(process.isTerminal()).isFalse();
    }

    @Test
    void pendingCanBeApproved() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH).approve();
        assertThat(process.processState()).isEqualTo(ProcessState.APPROVED);
    }

    @Test
    void pendingCanBeRejected() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH).reject();
        assertThat(process.processState()).isEqualTo(ProcessState.REJECTED);
    }

    @Test
    void pendingCanBeCancelled() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH).cancel();
        assertThat(process.processState()).isEqualTo(ProcessState.CANCELLED);
    }

    @Test
    void pendingCanBeExpired() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH).expire();
        assertThat(process.processState()).isEqualTo(ProcessState.EXPIRED);
    }

    @Test
    void pendingCanBeClosed() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH).close();
        assertThat(process.processState()).isEqualTo(ProcessState.CLOSED);
    }

    @ParameterizedTest
    @MethodSource("terminalTransitions")
    void terminalStatesAreTerminal(UnaryOperator<AuthProcess> toTerminal) {
        AuthProcess terminal = toTerminal.apply(AuthProcess.create("user1", AuthMethod.PUSH));
        assertThat(terminal.isTerminal()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("terminalTransitions")
    void terminalStatesCannotTransition(UnaryOperator<AuthProcess> toTerminal) {
        AuthProcess terminal = toTerminal.apply(AuthProcess.create("user1", AuthMethod.PUSH));
        assertThatThrownBy(terminal::approve)
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(terminal::cancel)
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(terminal::expire)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void processPreservesUserIdAndMethodAcrossTransitions() {
        AuthProcess approved = AuthProcess.create("user42", AuthMethod.SMS).approve();
        assertThat(approved.userId()).isEqualTo("user42");
        assertThat(approved.authMethod()).isEqualTo(AuthMethod.SMS);
    }

    @Test
    void assignDeliveryPreservesState() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH)
                .assignDelivery("delivery-123", null);
        assertThat(process.deliveryId()).isEqualTo("delivery-123");
        assertThat(process.processState()).isEqualTo(ProcessState.PENDING);
    }

    @Test
    void eachProcessHasUniqueId() {
        AuthProcess p1 = AuthProcess.create("user1", AuthMethod.PUSH);
        AuthProcess p2 = AuthProcess.create("user1", AuthMethod.PUSH);
        assertThat(p1.id()).isNotEqualTo(p2.id());
    }

    @Test
    void updatedAtChangesOnTransition() throws InterruptedException {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        Thread.sleep(5);
        AuthProcess approved = process.approve();
        assertThat(approved.updatedAt()).isAfter(process.updatedAt());
    }

    static Stream<UnaryOperator<AuthProcess>> terminalTransitions() {
        return Stream.of(
                AuthProcess::approve,
                AuthProcess::reject,
                AuthProcess::cancel,
                AuthProcess::expire,
                AuthProcess::close
        );
    }
}