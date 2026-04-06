package pl.kathelan.functional.feature3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class UnaryOperatorExerciseTest {

    // --- identity ---

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — identity powinien zwracać ten sam obiekt")
    void identity_shouldReturnSameObject() {
        UnaryOperator<String> id = UnaryOperatorExercise.identity();
        String value = "hello";

        assertThat(id.apply(value)).isSameAs(value);
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — identity dla null powinien zwrócić null")
    void identity_shouldReturnNullForNull() {
        UnaryOperator<String> id = UnaryOperatorExercise.identity();

        assertThat(id.apply(null)).isNull();
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — identity na liczbach powinien zwracać tę samą wartość")
    void identity_shouldReturnSameIntegerValue() {
        UnaryOperator<Integer> id = UnaryOperatorExercise.identity();

        assertThat(id.apply(42)).isEqualTo(42);
    }

    // --- chain ---

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — chain powinien złożyć transformacje String w kolejności: trim, upper, prefix")
    void chain_shouldComposeStringTransformationsInOrder() {
        UnaryOperator<String> trimmed = String::trim;
        UnaryOperator<String> uppercased = String::toUpperCase;
        UnaryOperator<String> prefixed = s -> "PREFIX_" + s;

        UnaryOperator<String> pipeline = UnaryOperatorExercise.chain(List.of(trimmed, uppercased, prefixed));

        assertThat(pipeline.apply("  hello  ")).isEqualTo("PREFIX_HELLO");
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — chain z pustą listą powinien być identity")
    void chain_emptyListShouldBeIdentity() {
        UnaryOperator<Integer> chained = UnaryOperatorExercise.chain(List.of());

        assertThat(chained.apply(42)).isEqualTo(42);
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — chain powinien stosować operatory w kolejności listy, nie odwrotnie")
    void chain_shouldApplyInListOrderNotReverse() {
        // trim first, then suffix — not suffix then trim
        List<String> orderLog = new java.util.ArrayList<>();

        UnaryOperator<String> first = s -> {
            orderLog.add("first");
            return s.trim();
        };
        UnaryOperator<String> second = s -> {
            orderLog.add("second");
            return s + "_done";
        };

        UnaryOperatorExercise.chain(List.of(first, second)).apply("  val  ");

        assertThat(orderLog).containsExactly("first", "second");
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — chain z jednym operatorem powinien zachować się jak ten operator")
    void chain_singleOperatorBehavesLikeThatOperator() {
        UnaryOperator<Integer> doubled = n -> n * 2;
        UnaryOperator<Integer> chained = UnaryOperatorExercise.chain(List.of(doubled));

        assertThat(chained.apply(5)).isEqualTo(10);
    }

    // --- applyN ---

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — applyN powinien zastosować operator dokładnie N razy")
    void applyN_shouldApplyOperatorExactlyNTimes() {
        UnaryOperator<Integer> doubler = n -> n * 2;

        // 1 * 2 * 2 * 2 = 8
        assertThat(UnaryOperatorExercise.applyN(1, doubler, 3)).isEqualTo(8);
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — applyN z times=0 powinien zwrócić oryginalną wartość")
    void applyN_zeroTimesShouldReturnOriginalValue() {
        UnaryOperator<String> addBang = s -> s + "!";

        assertThat(UnaryOperatorExercise.applyN("hello", addBang, 0)).isEqualTo("hello");
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — applyN z times=1 powinien zachować się jak jednokrotne wywołanie")
    void applyN_onceEquivalentToSingleCall() {
        UnaryOperator<String> upper = String::toUpperCase;

        assertThat(UnaryOperatorExercise.applyN("abc", upper, 1)).isEqualTo("ABC");
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — applyN string concatenation powinien powtórzyć sufiks N razy")
    void applyN_stringSuffixRepeatedNTimes() {
        UnaryOperator<String> addDot = s -> s + ".";

        assertThat(UnaryOperatorExercise.applyN("end", addDot, 3)).isEqualTo("end...");
    }

    // --- conditional ---

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — conditional powinien zastosować operator gdy warunek jest true")
    void conditional_shouldApplyOperatorWhenConditionTrue() {
        UnaryOperator<Integer> doubled = UnaryOperatorExercise.conditional(n -> n > 0, n -> n * 2);

        assertThat(doubled.apply(5)).isEqualTo(10);
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — conditional powinien zwrócić oryginalną wartość gdy warunek jest false")
    void conditional_shouldReturnOriginalWhenConditionFalse() {
        UnaryOperator<Integer> doubled = UnaryOperatorExercise.conditional(n -> n > 0, n -> n * 2);

        assertThat(doubled.apply(-3)).isEqualTo(-3);
    }

    @Test
    @DisplayName("FEATURE 3: UnaryOperator — conditional powinien działać selektywnie na liście elementów")
    void conditional_shouldActSelectivelyOnList() {
        UnaryOperator<String> upper = UnaryOperatorExercise.conditional(
                s -> s.startsWith("A"),
                String::toUpperCase
        );

        List<String> words = List.of("Alice", "Bob", "Anna");
        List<String> result = words.stream().map(upper::apply).toList();

        assertThat(result).containsExactly("ALICE", "Bob", "ANNA");
    }
}
