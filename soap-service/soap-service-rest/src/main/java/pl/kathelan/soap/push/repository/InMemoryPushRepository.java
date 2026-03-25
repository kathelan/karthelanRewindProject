package pl.kathelan.soap.push.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.kathelan.soap.push.domain.PushRecord;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.exception.DeliveryNotFoundException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
@Profile("local")
public class InMemoryPushRepository implements PushRepository {

    private final Map<String, PushRecord> store = new ConcurrentHashMap<>();

    @Override
    public PushRecord save(PushRecord record) {
        store.put(record.getDeliveryId(), record);
        log.debug("save: deliveryId={}, status={}", record.getDeliveryId(), record.getStatus());
        return record;
    }

    @Override
    public Optional<PushRecord> findByDeliveryId(String deliveryId) {
        log.debug("findByDeliveryId: deliveryId={}", deliveryId);
        return Optional.ofNullable(store.get(deliveryId));
    }

    @Override
    public void updateStatus(String deliveryId, PushStatus status) {
        boolean updated = store.computeIfPresent(deliveryId, (k, v) -> v.withStatus(status)) != null;
        if (!updated) {
            throw new DeliveryNotFoundException(deliveryId);
        }
        log.info("updateStatus: deliveryId={}, status={}", deliveryId, status);
    }

    @Override
    public List<PushRecord> findPendingOlderThan(Duration age) {
        LocalDateTime threshold = LocalDateTime.now().minus(age);
        return store.values().stream()
                .filter(r -> r.getStatus() == PushStatus.PENDING)
                .filter(r -> r.getCreatedAt().isBefore(threshold))
                .toList();
    }
}