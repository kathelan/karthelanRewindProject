package pl.kathelan.soap.push.repository;

import pl.kathelan.soap.push.domain.PushRecord;
import pl.kathelan.soap.push.domain.PushStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface PushRepository {

    PushRecord save(PushRecord record);

    Optional<PushRecord> findByDeliveryId(String deliveryId);

    void updateStatus(String deliveryId, PushStatus status);

    List<PushRecord> findPendingOlderThan(Duration age);
}