package pl.kathelan.soap.push.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.soap.push.domain.PushRecord;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.exception.DeliveryNotFoundException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryPushRepositoryTest {

    private InMemoryPushRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPushRepository();
    }

    @Test
    void shouldSaveAndFindByDeliveryId() {
        PushRecord record = buildRecord("delivery-1", "user-1", "proc-1", PushStatus.PENDING);

        repository.save(record);
        Optional<PushRecord> result = repository.findByDeliveryId("delivery-1");

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo("user-1");
        assertThat(result.get().getProcessId()).isEqualTo("proc-1");
        assertThat(result.get().getStatus()).isEqualTo(PushStatus.PENDING);
    }

    @Test
    void shouldReturnEmptyWhenDeliveryNotFound() {
        Optional<PushRecord> result = repository.findByDeliveryId("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldUpdateStatus() {
        PushRecord record = buildRecord("delivery-2", "user-1", "proc-2", PushStatus.PENDING);
        repository.save(record);

        repository.updateStatus("delivery-2", PushStatus.APPROVED);

        Optional<PushRecord> updated = repository.findByDeliveryId("delivery-2");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(PushStatus.APPROVED);
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentDelivery() {
        assertThatThrownBy(() -> repository.updateStatus("ghost", PushStatus.APPROVED))
                .isInstanceOf(DeliveryNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void shouldFindPendingOlderThan() {
        PushRecord old = buildRecordAt("old-delivery", "user-1", "proc-old",
                PushStatus.PENDING, LocalDateTime.now().minusMinutes(10));
        PushRecord recent = buildRecordAt("new-delivery", "user-2", "proc-new",
                PushStatus.PENDING, LocalDateTime.now().minusSeconds(30));
        repository.save(old);
        repository.save(recent);

        List<PushRecord> expired = repository.findPendingOlderThan(Duration.ofMinutes(5));

        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getDeliveryId()).isEqualTo("old-delivery");
    }

    @Test
    void shouldNotReturnNonPendingInExpiredSearch() {
        PushRecord approved = buildRecordAt("approved-delivery", "user-1", "proc-1",
                PushStatus.APPROVED, LocalDateTime.now().minusMinutes(10));
        repository.save(approved);

        List<PushRecord> expired = repository.findPendingOlderThan(Duration.ofMinutes(5));

        assertThat(expired).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenNoPendingExpired() {
        List<PushRecord> result = repository.findPendingOlderThan(Duration.ofMinutes(5));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldPreserveAllFieldsOnSave() {
        LocalDateTime now = LocalDateTime.now();
        PushRecord record = buildRecordAt("d-full", "u-full", "p-full", PushStatus.PENDING, now);

        repository.save(record);
        PushRecord found = repository.findByDeliveryId("d-full").orElseThrow();

        assertThat(found.getDeliveryId()).isEqualTo("d-full");
        assertThat(found.getUserId()).isEqualTo("u-full");
        assertThat(found.getProcessId()).isEqualTo("p-full");
        assertThat(found.getStatus()).isEqualTo(PushStatus.PENDING);
        assertThat(found.getCreatedAt()).isEqualTo(now);
    }

    // ===== helpers =====

    private PushRecord buildRecord(String deliveryId, String userId, String processId, PushStatus status) {
        return buildRecordAt(deliveryId, userId, processId, status, LocalDateTime.now());
    }

    private PushRecord buildRecordAt(String deliveryId, String userId, String processId,
                                     PushStatus status, LocalDateTime createdAt) {
        return PushRecord.builder()
                .deliveryId(deliveryId)
                .userId(userId)
                .processId(processId)
                .status(status)
                .createdAt(createdAt)
                .build();
    }
}