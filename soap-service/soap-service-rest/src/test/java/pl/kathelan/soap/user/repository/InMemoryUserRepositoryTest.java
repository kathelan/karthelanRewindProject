package pl.kathelan.soap.user.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.kathelan.soap.user.domain.Address;
import pl.kathelan.soap.user.domain.User;
import pl.kathelan.soap.user.exception.UserAlreadyExistsException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryUserRepositoryTest {

    private InMemoryUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
    }

    @Test
    void shouldSaveUserAndGenerateId() {
        User user = buildUser("test@example.com", "Warsaw");

        User saved = repository.save(user);

        assertThat(saved.getId()).isNotNull().isNotBlank();
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void shouldFindUserById() {
        User saved = repository.save(buildUser("find@example.com", "Warsaw"));

        Optional<User> result = repository.findById(saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("find@example.com");
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        Optional<User> result = repository.findById("nonexistent-id");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindUsersByCity() {
        repository.save(buildUser("a@example.com", "Warsaw"));
        repository.save(buildUser("b@example.com", "Warsaw"));
        repository.save(buildUser("c@example.com", "Krakow"));

        List<User> result = repository.findByCity("Warsaw");

        assertThat(result)
                .hasSize(2)
                .allMatch(u -> u.getAddress().getCity().equals("Warsaw"));
    }

    @Test
    void shouldReturnEmptyListWhenNoCityMatch() {
        repository.save(buildUser("a@example.com", "Warsaw"));

        List<User> result = repository.findByCity("Berlin");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnTrueWhenEmailExists() {
        repository.save(buildUser("exists@example.com", "Warsaw"));

        assertThat(repository.existsByEmail("exists@example.com")).isTrue();
    }

    @Test
    void shouldReturnFalseWhenEmailNotExists() {
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void shouldThrowWhenSavingDuplicateEmail() {
        repository.save(buildUser("dup@example.com", "Warsaw"));

        User duplicate = buildUser("dup@example.com", "Krakow");
        assertThatThrownBy(() -> repository.save(duplicate))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("dup@example.com");
    }

    @Test
    void shouldPreserveAllFieldsOnSave() {
        User user = buildUser("full@example.com", "Gdansk");

        User saved = repository.save(user);

        assertThat(saved.getFirstName()).isEqualTo("Jan");
        assertThat(saved.getLastName()).isEqualTo("Kowalski");
        assertThat(saved.getAddress().getStreet()).isEqualTo("ul. Testowa 1");
        assertThat(saved.getAddress().getZipCode()).isEqualTo("00-001");
        assertThat(saved.getAddress().getCountry()).isEqualTo("Poland");
    }

    // ===== helpers =====

    private User buildUser(String email, String city) {
        return User.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email(email)
                .address(Address.builder()
                        .street("ul. Testowa 1")
                        .city(city)
                        .zipCode("00-001")
                        .country("Poland")
                        .build())
                .build();
    }
}
