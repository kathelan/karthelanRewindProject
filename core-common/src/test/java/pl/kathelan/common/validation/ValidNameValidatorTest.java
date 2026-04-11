package pl.kathelan.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @ValidName — adnotacja walidacyjna Bean Validation dla imion i nazwisk.
 *
 * Akceptuje:
 *   - litery Unicode (w tym polskie znaki: ą, ę, ó itd.)
 *   - łączniki (-) i apostrofy (') jako separatory członów (Anne-Marie, O'Brien)
 *   - spacje między członami imienia/nazwiska
 *   - długość od 2 do 50 znaków
 *
 * Odrzuca:
 *   - cyfry (imiona nie zawierają cyfr)
 *   - znaki specjalne (!, @, # itp.)
 *   - zbyt krótkie (< 2 znaków) i zbyt długie (> 50 znaków)
 *   - same spacje
 *
 * Wartość null jest traktowana jako poprawna — za null odpowiada @NotNull.
 */
@DisplayName("@ValidName — walidator imion i nazwisk")
class ValidNameValidatorTest {

    private final ValidNameValidator validator = new ValidNameValidator();

    @Nested
    @DisplayName("Poprawne imiona i nazwiska")
    class ValidNames {

        @ParameterizedTest(name = "\"{0}\" powinno być zaakceptowane")
        @ValueSource(strings = {"Jan", "Anna", "Anne-Marie", "O'Brien", "Józef", "Ańa Bła"})
        @DisplayName("akceptuje popularne formaty imion i nazwisk")
        void acceptsValidNames(String name) {
            assertThat(validator.isValid(name, null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje null — za null odpowiada @NotNull, nie @ValidName")
        void acceptsNull() {
            assertThat(validator.isValid(null, null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje imię o maksymalnej dopuszczalnej długości (50 znaków)")
        void acceptsNameAtMaxLength() {
            String maxLength = "A".repeat(50);
            assertThat(validator.isValid(maxLength, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("Niepoprawne wartości")
    class InvalidNames {

        @ParameterizedTest(name = "\"{0}\" powinno być odrzucone")
        @ValueSource(strings = {"J", "Jan123", "  ", "Name!", "a", "123"})
        @DisplayName("odrzuca imiona z cyframi, znakami specjalnymi lub za krótkie")
        void rejectsInvalidNames(String name) {
            assertThat(validator.isValid(name, null)).isFalse();
        }

        @Test
        @DisplayName("odrzuca imię przekraczające 50 znaków")
        void rejectsNameTooLong() {
            String tooLong = "A".repeat(51);
            assertThat(validator.isValid(tooLong, null)).isFalse();
        }
    }
}
