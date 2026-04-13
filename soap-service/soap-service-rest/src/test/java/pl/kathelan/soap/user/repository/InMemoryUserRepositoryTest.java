package pl.kathelan.soap.user.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.kathelan.soap.user.domain.Address;
import pl.kathelan.soap.user.domain.User;
import pl.kathelan.soap.user.exception.UserAlreadyExistsException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * InMemoryUserRepository — unit tests for the in-memory user store.
 *
 * <p>Verifies the contract of {@link InMemoryUserRepository}: ID generation on save,
 * lookup by ID, filtering by city, email uniqueness enforcement, and full field preservation.
 * Each test creates a fresh repository instance to ensure full isolation.
 */
@DisplayName("InMemoryUserRepository — in-memory user store behaviour")
class InMemoryUserRepositoryTest {

    private InMemoryUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
    }

    // ===== save =====

    @Nested
    @DisplayName("save — persisting a user")
    class Save {

        /**
         * ID generation: every saved user must receive a non-null, non-blank UUID-style id
         * even though no id was set on the input object. The email must be preserved exactly.
         */
        @Test
        @DisplayName("assigns a generated id and preserves email on first save")
        void shouldSaveUserAndGenerateId() {
            User user = buildUser("test@example.com", "Warsaw");

            User saved = repository.save(user);

            assertThat(saved.getId()).isNotNull().isNotBlank();
            assertThat(saved.getEmail()).isEqualTo("test@example.com");
        }

        /**
         * Duplicate email guard: saving two users with the same email address must throw
         * {@link UserAlreadyExistsException} (not a generic exception), and the message
         * must contain the duplicate email so the caller can log it meaningfully.
         */
        @Test
        @DisplayName("throws UserAlreadyExistsException containing the email when email is duplicated")
        void shouldThrowWhenSavingDuplicateEmail() {
            repository.save(buildUser("dup@example.com", "Warsaw"));

            User duplicate = buildUser("dup@example.com", "Krakow");
            assertThatThrownBy(() -> repository.save(duplicate))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("dup@example.com");
        }

        /**
         * Full field preservation: all domain fields (firstName, lastName, address fields)
         * must survive the save round-trip without modification or loss.
         */
        @Test
        @DisplayName("preserves all user fields after save")
        void shouldPreserveAllFieldsOnSave() {
            User user = buildUser("full@example.com", "Gdansk");

            User saved = repository.save(user);

            assertThat(saved.getFirstName()).isEqualTo("Jan");
            assertThat(saved.getLastName()).isEqualTo("Kowalski");
            assertThat(saved.getAddress().getStreet()).isEqualTo("ul. Testowa 1");
            assertThat(saved.getAddress().getZipCode()).isEqualTo("00-001");
            assertThat(saved.getAddress().getCountry()).isEqualTo("Poland");
        }
    }

    // ===== findById =====

    @Nested
    @DisplayName("findById — looking up a user by generated id")
    class FindById {

        /**
         * Existing user: after saving, the user must be retrievable by the generated id,
         * and the returned Optional must contain the correct email.
         */
        @Test
        @DisplayName("returns the user wrapped in Optional.of for a known id")
        void shouldFindUserById() {
            User saved = repository.save(buildUser("find@example.com", "Warsaw"));

            Optional<User> result = repository.findById(saved.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("find@example.com");
        }

        /**
         * Unknown id: querying a random id that was never saved must return
         * {@code Optional.empty()} rather than throwing or returning null.
         */
        @Test
        @DisplayName("returns Optional.empty for an id that was never saved")
        void shouldReturnEmptyWhenUserNotFound() {
            Optional<User> result = repository.findById("nonexistent-id");

            assertThat(result).isEmpty();
        }
    }

    // ===== findByCity =====

    @Nested
    @DisplayName("findByCity — filtering users by address city")
    class FindByCity {

        /**
         * City match: only users whose address city equals the queried city should be returned.
         * Users in other cities must be excluded from the result.
         */
        @Test
        @DisplayName("returns only users from the queried city, excluding users from other cities")
        void shouldFindUsersByCity() {
            repository.save(buildUser("a@example.com", "Warsaw"));
            repository.save(buildUser("b@example.com", "Warsaw"));
            repository.save(buildUser("c@example.com", "Krakow"));

            List<User> result = repository.findByCity("Warsaw");

            assertThat(result)
                    .hasSize(2)
                    .allMatch(u -> u.getAddress().getCity().equals("Warsaw"));
        }

        /**
         * No match: when no user exists in the requested city the method must return
         * an empty list (not null), so callers can safely iterate without null checks.
         */
        @Test
        @DisplayName("returns empty list when no users exist in the queried city")
        void shouldReturnEmptyListWhenNoCityMatch() {
            repository.save(buildUser("a@example.com", "Warsaw"));

            List<User> result = repository.findByCity("Berlin");

            assertThat(result).isEmpty();
        }
    }

    // ===== existsByEmail =====

    @Nested
    @DisplayName("existsByEmail — checking email uniqueness")
    class ExistsByEmail {

        /**
         * Known email: after saving a user, {@code existsByEmail} must return {@code true}
         * for the exact email that was used.
         */
        @Test
        @DisplayName("returns true when the email belongs to a saved user")
        void shouldReturnTrueWhenEmailExists() {
            repository.save(buildUser("exists@example.com", "Warsaw"));

            assertThat(repository.existsByEmail("exists@example.com")).isTrue();
        }

        /**
         * Unknown email: {@code existsByEmail} must return {@code false} for any email
         * that was never persisted, enabling pre-save uniqueness checks in the service layer.
         */
        @Test
        @DisplayName("returns false when the email does not belong to any saved user")
        void shouldReturnFalseWhenEmailNotExists() {
            assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
        }
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
