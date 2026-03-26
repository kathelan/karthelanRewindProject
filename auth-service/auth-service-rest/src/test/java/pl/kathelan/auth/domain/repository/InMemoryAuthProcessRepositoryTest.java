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
    void findPendingOlderThanReturnsOldPendingProcesses() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        repository.save(process);

        List<AuthProcess> old = repository.findPendingOlderThan(LocalDateTime.now().plusSeconds(1));
        assertThat(old).hasSize(1);
    }

    @Test
    void findPendingOlderThanIgnoresRecentProcesses() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        repository.save(process);

        List<AuthProcess> old = repository.findPendingOlderThan(LocalDateTime.now().minusSeconds(10));
        assertThat(old).isEmpty();
    }

    @Test
    void findPendingOlderThanIgnoresTerminalProcesses() {
        AuthProcess process = AuthProcess.create("user1", AuthMethod.PUSH);
        repository.save(process.expire());

        List<AuthProcess> old = repository.findPendingOlderThan(LocalDateTime.now().plusSeconds(1));
        assertThat(old).isEmpty();
    }
}