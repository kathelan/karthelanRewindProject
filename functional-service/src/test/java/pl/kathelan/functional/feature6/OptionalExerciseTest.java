package pl.kathelan.functional.feature6;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.kathelan.functional.feature6.model.User;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalExerciseTest {

    private static final List<User> USERS = List.of(
            User.builder().name("Anna").age(17).email("anna@example.com").build(),
            User.builder().name("Piotr").age(25).email("piotr@example.com").build(),
            User.builder().name("Maria").age(30).email("maria@example.com").build()
    );

    // -----------------------------------------------------------------------
    // findFirstAdult
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Optional — findFirstAdult zwraca pierwszego dorosłego użytkownika")
    void findFirstAdultReturnsFirstUserWithAgeAtLeast18() {
        Optional<User> result = OptionalExercise.findFirstAdult(USERS);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Piotr");
    }

    @Test
    @DisplayName("FEATURE 6: Optional — findFirstAdult zwraca empty dla listy dzieci")
    void findFirstAdultReturnsEmptyWhenAllUsersAreMinors() {
        List<User> minors = List.of(
                User.builder().name("Kacper").age(15).email("k@test.pl").build(),
                User.builder().name("Zosia").age(12).email("z@test.pl").build()
        );

        Optional<User> result = OptionalExercise.findFirstAdult(minors);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 6: Optional — findFirstAdult zwraca empty dla pustej listy")
    void findFirstAdultReturnsEmptyForEmptyList() {
        Optional<User> result = OptionalExercise.findFirstAdult(List.of());

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // mapToEmail
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Optional — mapToEmail zwraca email gdy użytkownik istnieje")
    void mapToEmailReturnsEmailWhenPresent() {
        Optional<User> user = Optional.of(
                User.builder().name("Jan").age(22).email("jan@example.com").build()
        );

        Optional<String> email = OptionalExercise.mapToEmail(user);

        assertThat(email).hasValue("jan@example.com");
    }

    @Test
    @DisplayName("FEATURE 6: Optional — mapToEmail zwraca empty gdy Optional jest pusty")
    void mapToEmailReturnsEmptyWhenOptionalIsEmpty() {
        Optional<String> email = OptionalExercise.mapToEmail(Optional.empty());

        assertThat(email).isEmpty();
    }

    // -----------------------------------------------------------------------
    // getNameOrDefault
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Optional — getNameOrDefault zwraca imię użytkownika gdy obecny")
    void getNameOrDefaultReturnsUserNameWhenPresent() {
        Optional<User> user = Optional.of(
                User.builder().name("Katarzyna").age(28).email("k@pl.com").build()
        );

        String name = OptionalExercise.getNameOrDefault(user, "Nieznany");

        assertThat(name).isEqualTo("Katarzyna");
    }

    @Test
    @DisplayName("FEATURE 6: Optional — getNameOrDefault zwraca domyślną nazwę gdy Optional jest pusty")
    void getNameOrDefaultReturnsDefaultWhenEmpty() {
        String name = OptionalExercise.getNameOrDefault(Optional.empty(), "Gość");

        assertThat(name).isEqualTo("Gość");
    }

    // -----------------------------------------------------------------------
    // findUserEmail
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Optional — findUserEmail zwraca email dopasowanego użytkownika")
    void findUserEmailReturnsEmailWhenUserFound() {
        Optional<String> email = OptionalExercise.findUserEmail(USERS, "Maria");

        assertThat(email).hasValue("maria@example.com");
    }

    @Test
    @DisplayName("FEATURE 6: Optional — findUserEmail zwraca empty gdy użytkownik nie istnieje")
    void findUserEmailReturnsEmptyWhenUserNotFound() {
        Optional<String> email = OptionalExercise.findUserEmail(USERS, "Nieznajomy");

        assertThat(email).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 6: Optional — findUserEmail zwraca empty gdy email użytkownika jest null")
    void findUserEmailReturnsEmptyWhenEmailIsNull() {
        List<User> users = List.of(
                User.builder().name("NoEmail").age(30).email(null).build()
        );

        Optional<String> email = OptionalExercise.findUserEmail(users, "NoEmail");

        assertThat(email).isEmpty();
    }
}
