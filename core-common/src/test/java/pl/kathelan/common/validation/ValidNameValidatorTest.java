package pl.kathelan.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidNameValidatorTest {

    private final ValidNameValidator validator = new ValidNameValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Jan", "Anna", "Anne-Marie", "O'Brien", "Józef", "Ańa Bła"})
    void shouldAcceptValidNames(String name) {
        assertThat(validator.isValid(name, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"J", "Jan123", "  ", "Name!", "a", "123"})
    void shouldRejectInvalidNames(String name) {
        assertThat(validator.isValid(name, null)).isFalse();
    }

    @Test
    void shouldAcceptNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void shouldRejectNameTooLong() {
        String tooLong = "A".repeat(51);
        assertThat(validator.isValid(tooLong, null)).isFalse();
    }

    @Test
    void shouldAcceptNameAtMaxLength() {
        String maxLength = "A".repeat(50);
        assertThat(validator.isValid(maxLength, null)).isTrue();
    }
}
