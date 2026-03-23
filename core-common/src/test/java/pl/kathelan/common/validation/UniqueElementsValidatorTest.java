package pl.kathelan.common.validation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueElementsValidatorTest {

    private final UniqueElementsValidator validator = new UniqueElementsValidator();

    @Test
    void shouldAcceptListWithUniqueElements() {
        assertThat(validator.isValid(List.of("Warsaw", "Krakow", "Gdansk"), null)).isTrue();
    }

    @Test
    void shouldRejectListWithDuplicates() {
        assertThat(validator.isValid(List.of("Warsaw", "Krakow", "Warsaw"), null)).isFalse();
    }

    @Test
    void shouldAcceptEmptyList() {
        assertThat(validator.isValid(List.of(), null)).isTrue();
    }

    @Test
    void shouldAcceptSingleElement() {
        assertThat(validator.isValid(List.of("Warsaw"), null)).isTrue();
    }

    @Test
    void shouldAcceptNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void shouldAcceptSet() {
        // Set z definicji ma unikalne elementy
        assertThat(validator.isValid(Set.of("a", "b", "c"), null)).isTrue();
    }

    @Test
    void shouldDetectDuplicatesOfIntegers() {
        assertThat(validator.isValid(List.of(1, 2, 2, 3), null)).isFalse();
    }
}
