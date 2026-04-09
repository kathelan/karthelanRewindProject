package pl.kathelan.soap.user.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.kathelan.soap.user.domain.User;
import pl.kathelan.soap.user.exception.UserAlreadyExistsException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
@Profile("local")
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String id) {
        log.debug("findById: id={}", id);
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<User> findByCity(String city) {
        List<User> result = store.values().stream()
                .filter(u -> city.equals(u.getAddress().getCity()))
                .toList();
        log.debug("findByCity: city={}, found={}", city, result.size());
        return result;
    }

    @Override
    public User save(User user) {
        if (existsByEmail(user.getEmail())) {
            throw new UserAlreadyExistsException(user.getEmail());
        }
        User toStore = user.withId(UUID.randomUUID().toString());
        store.put(toStore.getId(), toStore);
        log.info("save: stored user id={}", toStore.getId());
        return toStore;
    }

    @Override
    public boolean existsByEmail(String email) {
        return store.values().stream()
                .anyMatch(u -> email.equals(u.getEmail()));
    }
}
