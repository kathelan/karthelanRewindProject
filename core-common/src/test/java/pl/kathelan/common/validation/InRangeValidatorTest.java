package pl.kathelan.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @InRange — adnotacja walidacyjna Bean Validation sprawdzająca czy liczba mieści się w zakresie [min, max].
 *
 * Przykład użycia:
 *   {@code @InRange(min = 1, max = 100) Integer age;}
 *
 * Obsługuje wszystkie typy Number (Integer, Long, itp.).
 * Wartość null jest traktowana jako poprawna — za null odpowiada @NotNull.
 * Granice zakresu są włączone (min i max są akceptowane).
 */
@DisplayName("@InRange — walidator zakresu liczbowego")
class InRangeValidatorTest {

    private InRangeValidator validatorFor(long min, long max) {
        InRange annotation = mock(InRange.class);
        when(annotation.min()).thenReturn(min);
        when(annotation.max()).thenReturn(max);
        InRangeValidator validator = new InRangeValidator();
        validator.initialize(annotation);
        return validator;
    }

    @Nested
    @DisplayName("Wartości mieszczące się w zakresie [1, 100]")
    class ValidValues {

        @Test
        @DisplayName("akceptuje wartość równą dolnej granicy (min włącznie)")
        void acceptsValueAtMin() {
            assertThat(validatorFor(1, 100).isValid(1, null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje wartość równą górnej granicy (max włącznie)")
        void acceptsValueAtMax() {
            assertThat(validatorFor(1, 100).isValid(100, null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje wartość w środku zakresu")
        void acceptsValueInMiddle() {
            assertThat(validatorFor(1, 100).isValid(50, null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje null — za null odpowiada @NotNull, nie @InRange")
        void acceptsNull() {
            assertThat(validatorFor(1, 100).isValid(null, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("Wartości poza zakresem [1, 100]")
    class InvalidValues {

        @Test
        @DisplayName("odrzuca wartość o 1 poniżej dolnej granicy")
        void rejectsValueBelowMin() {
            assertThat(validatorFor(1, 100).isValid(0, null)).isFalse();
        }

        @Test
        @DisplayName("odrzuca wartość o 1 powyżej górnej granicy")
        void rejectsValueAboveMax() {
            assertThat(validatorFor(1, 100).isValid(101, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Typy liczbowe i zakresy brzegowe")
    class EdgeCases {

        @Test
        @DisplayName("działa z Long.MAX_VALUE jako granicą górną")
        void worksWithLongMaxValue() {
            assertThat(validatorFor(0, Long.MAX_VALUE).isValid(Long.MAX_VALUE, null)).isTrue();
        }

        @Test
        @DisplayName("działa z zakresem ujemnym — np. temperatury poniżej zera")
        void worksWithNegativeRange() {
            InRangeValidator validator = validatorFor(-10, -1);

            assertThat(validator.isValid(-5, null)).isTrue();
            assertThat(validator.isValid(0, null)).isFalse(); // 0 jest poza zakresem [-10, -1]
        }
    }
}
