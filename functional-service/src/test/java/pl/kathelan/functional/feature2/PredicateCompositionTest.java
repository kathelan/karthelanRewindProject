package pl.kathelan.functional.feature2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class PredicateCompositionTest {

    // --- not ---

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — not powinien zanegować wynik predykatu")
    void not_shouldNegatePredicateResult() {
        Predicate<String> notEmpty = PredicateComposition.not(String::isEmpty);

        assertThat(notEmpty.test("hello")).isTrue();
        assertThat(notEmpty.test("")).isFalse();
    }

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — not(not(p)) powinien być równoważny p")
    void not_doubleNegationEquivalentToOriginal() {
        Predicate<Integer> positive = n -> n > 0;
        Predicate<Integer> doubleNegated = PredicateComposition.not(PredicateComposition.not(positive));

        assertThat(doubleNegated.test(5)).isTrue();
        assertThat(doubleNegated.test(-1)).isFalse();
    }

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — not powinien działać w filtrze strumienia")
    void not_shouldWorkInStreamFilter() {
        List<String> nonEmpty = List.of("a", "", "b", "", "c").stream()
                .filter(PredicateComposition.not(String::isEmpty))
                .toList();

        assertThat(nonEmpty).containsExactly("a", "b", "c");
    }

    // --- allMatch ---

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — allMatch powinien przejść gdy wszystkie predykaty zwracają true")
    void allMatch_shouldPassWhenAllPredicatesMatch() {
        Predicate<Integer> combined = PredicateComposition.allMatch(List.of(
                n -> n > 0,
                n -> n < 100,
                n -> n % 2 == 0
        ));

        assertThat(combined.test(10)).isTrue();
    }

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — allMatch powinien odrzucić gdy jeden predykat zwraca false")
    void allMatch_shouldFailWhenOnePredicateFails() {
        Predicate<Integer> combined = PredicateComposition.allMatch(List.of(
                n -> n > 0,
                n -> n % 2 == 0
        ));

        assertThat(combined.test(3)).isFalse();
    }

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — allMatch z pustą listą powinien zwracać true")
    void allMatch_emptyListAlwaysTrue() {
        Predicate<String> combined = PredicateComposition.allMatch(List.of());

        assertThat(combined.test("anything")).isTrue();
    }

    // --- anyMatch ---

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — anyMatch powinien przejść gdy jeden predykat zwraca true")
    void anyMatch_shouldPassWhenOnePredicateMatches() {
        Predicate<String> combined = PredicateComposition.anyMatch(List.of(
                s -> s.startsWith("A"),
                s -> s.length() > 10
        ));

        assertThat(combined.test("Alice")).isTrue(); // starts with A
    }

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — anyMatch powinien odrzucić gdy żaden predykat nie pasuje")
    void anyMatch_shouldFailWhenNoPredicateMatches() {
        Predicate<String> combined = PredicateComposition.anyMatch(List.of(
                s -> s.startsWith("A"),
                s -> s.length() > 10
        ));

        assertThat(combined.test("Bob")).isFalse();
    }

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — anyMatch z pustą listą powinien zwracać false")
    void anyMatch_emptyListAlwaysFalse() {
        Predicate<String> combined = PredicateComposition.anyMatch(List.of());

        assertThat(combined.test("anything")).isFalse();
    }

    // --- noneMatch ---

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — noneMatch powinien przejść gdy żaden predykat nie pasuje")
    void noneMatch_shouldPassWhenNoneMatch() {
        Predicate<Integer> combined = PredicateComposition.noneMatch(List.of(
                n -> n < 0,
                n -> n > 100
        ));

        assertThat(combined.test(50)).isTrue();
    }

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — noneMatch powinien odrzucić gdy choć jeden predykat pasuje")
    void noneMatch_shouldFailWhenAnyMatches() {
        Predicate<Integer> combined = PredicateComposition.noneMatch(List.of(
                n -> n < 0,
                n -> n > 100
        ));

        assertThat(combined.test(-5)).isFalse();
    }

    @Test
    @DisplayName("FEATURE 2: PredicateComposition — noneMatch z pustą listą powinien zawsze zwracać true")
    void noneMatch_emptyListAlwaysTrue() {
        Predicate<String> combined = PredicateComposition.noneMatch(List.of());

        assertThat(combined.test("anything")).isTrue();
    }
}
