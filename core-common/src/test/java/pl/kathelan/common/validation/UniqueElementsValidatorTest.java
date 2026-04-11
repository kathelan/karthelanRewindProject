package pl.kathelan.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @UniqueElements — adnotacja walidacyjna Bean Validation sprawdzająca czy kolekcja
 * nie zawiera duplikatów.
 *
 * Przykład użycia:
 *   {@code @UniqueElements List<String> tags;}
 *
 * Działa z dowolnym typem Collection (List, Set itp.).
 * Wartość null jest traktowana jako poprawna — za null odpowiada @NotNull.
 * Unikalność sprawdzana przez HashSet — używa equals() i hashCode() elementów.
 */
@DisplayName("@UniqueElements — walidator unikalności elementów kolekcji")
class UniqueElementsValidatorTest {

    private final UniqueElementsValidator validator = new UniqueElementsValidator();

    @Nested
    @DisplayName("Kolekcje z unikalnymi elementami — powinny przejść walidację")
    class ValidCollections {

        @Test
        @DisplayName("akceptuje listę z różnymi elementami")
        void acceptsListWithUniqueElements() {
            assertThat(validator.isValid(List.of("Warsaw", "Krakow", "Gdansk"), null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje pustą listę")
        void acceptsEmptyList() {
            assertThat(validator.isValid(List.of(), null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje listę z jednym elementem")
        void acceptsSingleElement() {
            assertThat(validator.isValid(List.of("Warsaw"), null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje Set — z definicji nie może zawierać duplikatów")
        void acceptsSet() {
            assertThat(validator.isValid(Set.of("a", "b", "c"), null)).isTrue();
        }

        @Test
        @DisplayName("akceptuje null — za null odpowiada @NotNull, nie @UniqueElements")
        void acceptsNull() {
            assertThat(validator.isValid(null, null)).isTrue();
        }
    }

    @Nested
    @DisplayName("Kolekcje z duplikatami — powinny nie przejść walidacji")
    class InvalidCollections {

        @Test
        @DisplayName("odrzuca listę Stringów z duplikatem")
        void rejectsListWithDuplicateStrings() {
            assertThat(validator.isValid(List.of("Warsaw", "Krakow", "Warsaw"), null)).isFalse();
        }

        @Test
        @DisplayName("odrzuca listę liczb całkowitych z duplikatem")
        void rejectsListWithDuplicateIntegers() {
            assertThat(validator.isValid(List.of(1, 2, 2, 3), null)).isFalse();
        }
    }
}
