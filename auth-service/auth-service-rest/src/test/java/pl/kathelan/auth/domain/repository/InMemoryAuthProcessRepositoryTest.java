package pl.kathelan.auth.domain.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.domain.AuthProcess;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuthProcessRepositoryTest {

    private InMemoryAuthProcessRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuthProcessRepository();
    }

    @Test
    void saveAndFindById() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        repository.save(process);

        Optional<AuthProcess> found = repository.findById(process.id());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(process.id());
    }

    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        assertThat(repository.findById(java.util.UUID.randomUUID())).isEmpty();
    }

    @Test
    void saveOverwritesExistingProcess() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        repository.save(process);
        AuthProcess approved = process.approve();
        repository.save(approved);

        Optional<AuthProcess> found = repository.findById(process.id());
        assertThat(found.get().processState()).isEqualTo(pl.kathelan.auth.api.dto.ProcessState.APPROVED);
    }

    @Test
    void findPendingByUserIdReturnsPendingProcess() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        repository.save(process);

        Optional<AuthProcess> found = repository.findPendingByUserId("user1");
        assertThat(found).isPresent();
    }

    @Test
    void findPendingByUserIdIgnoresTerminalProcesses() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        repository.save(process.approve());

        assertThat(repository.findPendingByUserId("user1")).isEmpty();
    }

    @Test
    void findPendingByUserIdReturnsEmptyWhenNoProcessForUser() {
        assertThat(repository.findPendingByUserId("unknown")).isEmpty();
    }

    @Test
    void findAllPendingReturnsPendingProcesses() {
        repository.save(AuthProcess.create("user1", AuthMethod.PUSH));
        repository.save(AuthProcess.create("user2", AuthMethod.PUSH));

        List<AuthProcess> pending = repository.findAllPending();
        assertThat(pending).hasSize(2);
    }

    @Test
    void findAllPendingIgnoresTerminalProcesses() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        repository.save(process.expire());

        assertThat(repository.findAllPending()).isEmpty();
    }

    @Test
    void findPendingExpiredBefore_returnsExpiredProcesses() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        AuthProcess withExpiry = process.assignDelivery("d1", LocalDateTime.now().minusMinutes(1));
        repository.save(withExpiry);

        List<AuthProcess> result = repository.findPendingExpiredBefore(LocalDateTime.now());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(process.id());
    }

    @Test
    void findPendingExpiredBefore_ignoresNotYetExpired() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        AuthProcess withExpiry = process.assignDelivery("d1", LocalDateTime.now().plusMinutes(2));
        repository.save(withExpiry);

        assertThat(repository.findPendingExpiredBefore(LocalDateTime.now())).isEmpty();
    }

    @Test
    void findPendingExpiredBefore_ignoresTerminalProcesses() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        AuthProcess withExpiry = process.assignDelivery("d1", LocalDateTime.now().minusMinutes(1));
        repository.save(withExpiry.cancel());

        assertThat(repository.findPendingExpiredBefore(LocalDateTime.now())).isEmpty();
    }

    @Test
    void findPendingExpiredBefore_ignoresProcessesWithoutExpiresAt() {
        repository.save(AuthProcess.create("user1", AuthMethod.PUSH));

        assertThat(repository.findPendingExpiredBefore(LocalDateTime.now())).isEmpty();
    }
}