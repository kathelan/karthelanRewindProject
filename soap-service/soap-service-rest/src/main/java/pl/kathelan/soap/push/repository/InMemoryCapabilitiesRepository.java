package pl.kathelan.soap.push.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.kathelan.soap.push.domain.AuthMethod;
import pl.kathelan.soap.push.domain.UserCapabilities;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
@Profile("local")
public class InMemoryCapabilitiesRepository implements CapabilitiesRepository {

    private final Map<String, UserCapabilities> store = new ConcurrentHashMap<>();

    public InMemoryCapabilitiesRepository() {
        seed();
    }

    @Override
    public Optional<UserCapabilities> findByUserId(String userId) {
        log.debug("findByUserId: userId={}", userId);
        return Optional.ofNullable(store.get(userId));
    }

    private void seed() {
        store.put("user-push", UserCapabilities.builder()
                .userId("user-push")
                .active(true)
                .authMethods(List.of(AuthMethod.PUSH))
                .build());

        store.put("user-multi", UserCapabilities.builder()
                .userId("user-multi")
                .active(true)
                .authMethods(List.of(AuthMethod.PUSH, AuthMethod.SMS))
                .build());

        store.put("user-inactive", UserCapabilities.builder()
                .userId("user-inactive")
                .active(false)
                .authMethods(List.of(AuthMethod.PUSH))
                .build());
    }
}