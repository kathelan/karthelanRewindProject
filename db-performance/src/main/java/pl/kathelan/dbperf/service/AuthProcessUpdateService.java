package pl.kathelan.dbperf.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.kathelan.dbperf.entity.AuthProcess;
import pl.kathelan.dbperf.entity.AuthProcessStatus;
import pl.kathelan.dbperf.repository.AuthProcessRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuthProcessUpdateService {

    private final AuthProcessRepository repository;

    public AuthProcessUpdateService(AuthProcessRepository repository) {
        this.repository = repository;
    }

    /**
     * JPQL bulk UPDATE: single SQL statement, no prior SELECT.
     * Updates all rows matching userId + oldStatus in one query.
     * Each call = its own @Transactional = own BEGIN/COMMIT.
     */
    @Transactional
    public int updateByModifying(String userId, AuthProcessStatus oldStatus, AuthProcessStatus newStatus) {
        return repository.updateByUserIdAndStatus(userId, oldStatus, newStatus, LocalDateTime.now());
    }

    /**
     * All N updates in a SINGLE transaction.
     * One BEGIN + N×UPDATE + one COMMIT — eliminates per-call transaction overhead.
     */
    @Transactional
    public void updateAllInOneTransaction(List<String> userIds, AuthProcessStatus oldStatus, AuthProcessStatus newStatus) {
        LocalDateTime now = LocalDateTime.now();
        for (String userId : userIds) {
            repository.updateByUserIdAndStatus(userId, oldStatus, newStatus, now);
        }
    }

    /**
     * JPA save(): plain INSERT, one entity at a time.
     * Each call = separate @Transactional = separate BEGIN/COMMIT.
     */
    @Transactional
    public void insertBySave(String userId, AuthProcessStatus status) {
        AuthProcess entity = buildEntity(userId, status);
        repository.save(entity);
    }

    /**
     * saveAll(): builds {@code count} entities in memory, then sends them to the DB
     * in JDBC batches (batch_size=500 per application.properties).
     * One transaction, two JDBC round-trips instead of {@code count} round-trips.
     */
    @Transactional
    public void insertBatchBySaveAll(int count) {
        LocalDateTime now = LocalDateTime.now();
        List<AuthProcess> entities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AuthProcess entity = new AuthProcess();
            entity.setUserId("user_saveall_" + i);
            entity.setAuthMethod("BIOMETRIC");
            entity.setDeliveryId("delivery_saveall_" + i);
            entity.setStatus(AuthProcessStatus.PENDING);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            entity.setExpiresAt(now.plusMinutes(5));
            entities.add(entity);
        }
        repository.saveAll(entities);
    }

    private AuthProcess buildEntity(String userId, AuthProcessStatus status) {
        AuthProcess entity = new AuthProcess();
        entity.setUserId(userId);
        entity.setAuthMethod("BIOMETRIC");
        entity.setDeliveryId("delivery_jpa_" + System.nanoTime());
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return entity;
    }
}
