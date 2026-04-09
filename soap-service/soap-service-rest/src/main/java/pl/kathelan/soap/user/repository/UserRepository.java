package pl.kathelan.soap.user.repository;

import pl.kathelan.soap.user.domain.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(String id);

    List<User> findByCity(String city);

    User save(User user);

    boolean existsByEmail(String email);
}
