package pl.kathelan.soap.push.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * InMemoryPushRepository — unit tests for the in-memory push record store.
 *
 * <p>Verifies the contract of {@link InMemoryPushRepository}: save and retrieve by deliveryId,
 * status updates, querying for expired pending deliveries, and graceful handling of missing records.
 * Each test starts with an empty repository.
 */
@DisplayName("InMemoryPushRepository — in-memory push record store behaviour")
class InMemoryPushRepositoryTest {

    private InMemoryPushRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPushRepository();
    }

    // ===== save / findByDeliveryId =====

    @Nested
    @DisplayName("save and findByDeliveryId — basic persistence and lookup")
    class SaveAndFind {

        /**
         * Round-trip: a saved record must be retrievable by its deliveryId with all
         * domain fields (userId, processId, status) intact.
         */
        @Test
        @DisplayName("returns the saved record with correct userId, processId, and status")
        void shouldSaveAndFindByDeliveryId() {
            PushRecord record = buildRecord("delivery-1", "user-1", "proc-1", PushStatus.PENDING);

            repository.save(record);
            Optional<PushRecord> result = repository.findByDeliveryId("delivery-1");

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo("user-1");
            assertThat(result.get().getProcessId()).isEqualTo("proc-1");
            assertThat(result.get().getStatus()).isEqualTo(PushStatus.PENDING);
        }

        /**
         * Return value contract: {@code save} must return the record that was stored,
         * not null. Callers (e.g. endpoint handlers) rely on the returned object
         * to chain operations without a subsequent {@code findByDeliveryId} call.
         */
        @Test
        @DisplayName("returns the stored record (not null) as the result of save")
        void shouldReturnSavedRecordFromSave() {
            PushRecord record = buildRecord("delivery-ret", "user-r", "proc-r", PushStatus.PENDING);

            PushRecord returned = repository.save(record);

            assertThat(returned).isNotNull();
            assertThat(returned.getDeliveryId()).isEqualTo("delivery-ret");
            assertThat(returned.getStatus()).isEqualTo(PushStatus.PENDING);
        }

        /**
         * Unknown deliveryId: querying a deliveryId that was never saved must return
         * {@code Optional.empty()} — not null and not an exception.
         */
        @Test
        @DisplayName("returns Optional.empty for a deliveryId that was never saved")
        void shouldReturnEmptyWhenDeliveryNotFound() {
            Optional<PushRecord> result = repository.findByDeliveryId("nonexistent");

            assertThat(result).isEmpty();
        }

        /**
         * Full field preservation: all fields including createdAt must survive
         * the save/load round-trip without truncation or mutation.
         */
        @Test
        @DisplayName("preserves all fields (including createdAt) after save")
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
    }

    // ===== updateStatus =====

    @Nested
    @DisplayName("updateStatus — changing the status of an existing delivery")
    class UpdateStatus {

        /**
         * Status transition: after calling updateStatus the stored record must reflect
         * the new status. This models the simulator updating the delivery outcome.
         */
        @Test
        @DisplayName("changes the stored status from PENDING to APPROVED for an existing delivery")
        void shouldUpdateStatus() {
            PushRecord record = buildRecord("delivery-2", "user-1", "proc-2", PushStatus.PENDING);
            repository.save(record);

            repository.updateStatus("delivery-2", PushStatus.APPROVED);

            Optional<PushRecord> updated = repository.findByDeliveryId("delivery-2");
            assertThat(updated).isPresent();
            assertThat(updated.get().getStatus()).isEqualTo(PushStatus.APPROVED);
        }

        /**
         * Missing delivery: attempting to update the status of a deliveryId that does not
         * exist must throw {@link DeliveryNotFoundException} with the unknown id in the message,
         * not a generic NullPointerException or silently do nothing.
         */
        @Test
        @DisplayName("throws DeliveryNotFoundException containing the id for an unknown deliveryId")
        void shouldThrowWhenUpdatingNonExistentDelivery() {
            assertThatThrownBy(() -> repository.updateStatus("ghost", PushStatus.APPROVED))
                    .isInstanceOf(DeliveryNotFoundException.class)
                    .hasMessageContaining("ghost");
        }
    }

    // ===== findPendingOlderThan =====

    @Nested
    @DisplayName("findPendingOlderThan — discovering expired pending deliveries")
    class FindPendingOlderThan {

        /**
         * Expiry filtering: only records whose status is PENDING AND whose createdAt is
         * older than the given duration threshold must be returned.
         * A recently created PENDING record must not appear in the results.
         */
        @Test
        @DisplayName("returns only PENDING records older than the threshold, excluding recent ones")
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

        /**
         * Non-PENDING exclusion: a record that is old enough to be "expired" but has already
         * transitioned out of PENDING (e.g. APPROVED) must not appear in the result.
         * Only PENDING records should be polled for timeout processing.
         */
        @Test
        @DisplayName("excludes old records that are not in PENDING status")
        void shouldNotReturnNonPendingInExpiredSearch() {
            PushRecord approved = buildRecordAt("approved-delivery", "user-1", "proc-1",
                    PushStatus.APPROVED, LocalDateTime.now().minusMinutes(10));
            repository.save(approved);

            List<PushRecord> expired = repository.findPendingOlderThan(Duration.ofMinutes(5));

            assertThat(expired).isEmpty();
        }

        /**
         * Empty store: when there are no records at all the method must return an empty list,
         * never null.
         */
        @Test
        @DisplayName("returns empty list when the repository contains no records")
        void shouldReturnEmptyWhenNoPendingExpired() {
            List<PushRecord> result = repository.findPendingOlderThan(Duration.ofMinutes(5));

            assertThat(result).isEmpty();
        }
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
