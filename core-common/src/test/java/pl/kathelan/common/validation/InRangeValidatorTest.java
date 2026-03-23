package pl.kathelan.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InRangeValidatorTest {

    private InRangeValidator validatorFor(long min, long max) {
        InRange annotation = mock(InRange.class);
        when(annotation.min()).thenReturn(min);
        when(annotation.max()).thenReturn(max);
        InRangeValidator validator = new InRangeValidator();
        validator.initialize(annotation);
        return validator;
    }

    @Test
    void shouldAcceptValueAtMin() {
        assertThat(validatorFor(1, 100).isValid(1, null)).isTrue();
    }

    @Test
    void shouldAcceptValueAtMax() {
        assertThat(validatorFor(1, 100).isValid(100, null)).isTrue();
    }

    @Test
    void shouldAcceptValueInMiddle() {
        assertThat(validatorFor(1, 100).isValid(50, null)).isTrue();
    }

    @Test
    void shouldRejectValueBelowMin() {
        assertThat(validatorFor(1, 100).isValid(0, null)).isFalse();
    }

    @Test
    void shouldRejectValueAboveMax() {
        assertThat(validatorFor(1, 100).isValid(101, null)).isFalse();
    }

    @Test
    void shouldAcceptNull() {
        assertThat(validatorFor(1, 100).isValid(null, null)).isTrue();
    }

    @Test
    void shouldWorkWithLong() {
        assertThat(validatorFor(0, Long.MAX_VALUE).isValid(Long.MAX_VALUE, null)).isTrue();
    }

    @Test
    void shouldWorkWithNegativeRange() {
        assertThat(validatorFor(-10, -1).isValid(-5, null)).isTrue();
        assertThat(validatorFor(-10, -1).isValid(0, null)).isFalse();
    }
}
