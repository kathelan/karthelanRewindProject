package pl.kathelan.auth.domain.repository;

import pl.kathelan.auth.domain.AuthProcess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthProcessRepository {
    AuthProcess save(AuthProcess process);
    Optional<AuthProcess> findById(UUID id);
    Optional<AuthProcess> findPendingByUserId(String userId);
    List<AuthProcess> findAllPending();
}