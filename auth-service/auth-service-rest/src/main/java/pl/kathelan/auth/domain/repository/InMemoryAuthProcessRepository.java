package pl.kathelan.auth.domain.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.domain.AuthProcess;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryAuthProcessRepository implements AuthProcessRepository {

    private final ConcurrentHashMap<UUID, AuthProcess> store = new ConcurrentHashMap<>();

    @Override
    public AuthProcess save(AuthProcess process) {
        store.put(process.id(), process);
        return process;
    }

    @Override
    public Optional<AuthProcess> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<AuthProcess> findPendingByUserId(String userId) {
        return store.values().stream()
                .filter(p -> p.userId().equals(userId) && p.processState() == ProcessState.PENDING)
                .findFirst();
    }

    @Override
    public List<AuthProcess> findPendingOlderThan(LocalDateTime threshold) {
        return store.values().stream()
                .filter(p -> p.processState() == ProcessState.PENDING && p.createdAt().isBefore(threshold))
                .toList();
    }
}