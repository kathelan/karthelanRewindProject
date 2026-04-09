package pl.kathelan.soap.push.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.kathelan.soap.push.domain.UserCapabilities;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
@Profile("local")
public class InMemoryCapabilitiesRepository implements CapabilitiesRepository {

    private final Map<String, UserCapabilities> store = new ConcurrentHashMap<>();

    @Override
    public Optional<UserCapabilities> findByUserId(String userId) {
        log.debug("findByUserId: userId={}", userId);
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public void save(UserCapabilities capabilities) {
        store.put(capabilities.getUserId(), capabilities);
        log.debug("save: userId={}", capabilities.getUserId());
    }
}
