package pl.kathelan.functional.feature4;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.kathelan.functional.feature4.model.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorTest {

    private static final Validator<User> AGE_VALIDATOR =
            Validator.<User>of().addRule(u -> u.getAge() >= 18, "Wiek musi być >= 18");

    private static final Validator<User> EMAIL_VALIDATOR =
            Validator.<User>of().addRule(u -> u.getEmail() != null && u.getEmail().contains("@"), "Nieprawidłowy email");

    private static final Validator<User> NAME_VALIDATOR =
            Validator.<User>of().addRule(u -> u.getName() != null && !u.getName().isBlank(), "Imię wymagane");

    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 4: Validator — valid user przechodzi walidację")
    void validUserPassesAllRules() {
        Validator<User> validator = Validator.<User>of()
                .addRule(u -> u.getAge() >= 18, "Wiek musi być >= 18")
                .addRule(u -> u.getEmail().contains("@"), "Nieprawidłowy email")
                .addRule(u -> u.getName() != null && !u.getName().isBlank(), "Imię wymagane");

        User user = User.builder().name("Anna").email("anna@example.com").age(25).build();

        ValidationResult result = validator.validate(user);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 4: Validator — brak imienia zwraca błąd walidacji")
    void missingNameReturnsValidationError() {
        User user = User.builder().name(null).email("anna@example.com").age(25).build();

        ValidationResult result = NAME_VALIDATOR.validate(user);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).containsExactly("Imię wymagane");
    }

    @Test
    @DisplayName("FEATURE 4: Validator — wiek poniżej 18 zwraca błąd walidacji")
    void ageBelowMinimumReturnsValidationError() {
        User user = User.builder().name("Piotr").email("piotr@example.com").age(16).build();

        ValidationResult result = AGE_VALIDATOR.validate(user);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).containsExactly("Wiek musi być >= 18");
    }

    @Test
    @DisplayName("FEATURE 4: Validator — nieprawidłowy email zwraca błąd walidacji")
    void invalidEmailReturnsValidationError() {
        User user = User.builder().name("Jan").email("jan-bez-malpy").age(20).build();

        ValidationResult result = EMAIL_VALIDATOR.validate(user);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).containsExactly("Nieprawidłowy email");
    }

    @Test
    @DisplayName("FEATURE 4: Validator — wiele błędów zwraca wszystkie komunikaty")
    void multipleFailingRulesReturnAllMessages() {
        Validator<User> validator = Validator.<User>of()
                .addRule(u -> u.getAge() >= 18, "Wiek musi być >= 18")
                .addRule(u -> u.getEmail() != null && u.getEmail().contains("@"), "Nieprawidłowy email")
                .addRule(u -> u.getName() != null && !u.getName().isBlank(), "Imię wymagane");

        User user = User.builder().name(null).email("bez-malpy").age(15).build();

        ValidationResult result = validator.validate(user);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).containsExactlyInAnyOrder(
                "Wiek musi być >= 18",
                "Nieprawidłowy email",
                "Imię wymagane"
        );
    }

    @Test
    @DisplayName("FEATURE 4: Validator — and() łączy reguły z obu validatorów")
    void andCombinesBothValidators() {
        Validator<User> combined = AGE_VALIDATOR.and(EMAIL_VALIDATOR).and(NAME_VALIDATOR);

        User invalidUser = User.builder().name(null).email("bad").age(10).build();
        ValidationResult result = combined.validate(invalidUser);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).hasSize(3);
    }

    @Test
    @DisplayName("FEATURE 4: Validator — and() zwraca valid gdy oba validatory przechodzą")
    void andReturnsValidWhenBothPass() {
        Validator<User> combined = AGE_VALIDATOR.and(EMAIL_VALIDATOR);

        User validUser = User.builder().name("Maria").email("maria@test.pl").age(30).build();
        ValidationResult result = combined.validate(validUser);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 4: Validator — pusta lista reguł zawsze zwraca valid")
    void emptyValidatorAlwaysReturnsValid() {
        Validator<User> empty = Validator.of();
        User anyUser = User.builder().name(null).email(null).age(-99).build();

        ValidationResult result = empty.validate(anyUser);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 4: Validator — ValidationResult.failure zawiera przekazane błędy")
    void validationResultFailureContainsGivenErrors() {
        List<String> errors = List.of("Błąd A", "Błąd B");
        ValidationResult result = ValidationResult.failure(errors);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).containsExactlyElementsOf(errors);
    }

    @Test
    @DisplayName("FEATURE 4: Validator — ValidationResult.success jest zawsze valid i puste")
    void validationResultSuccessIsAlwaysValidAndEmpty() {
        ValidationResult result = ValidationResult.success();

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }
}
