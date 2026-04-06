package pl.kathelan.functional.feature3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryOperatorExerciseTest {

    // --- reduce ---

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — reduce powinien zsumować liczby całkowite")
    void reduce_shouldSumIntegers() {
        int result = BinaryOperatorExercise.reduce(List.of(1, 2, 3, 4, 5), 0, Integer::sum);

        assertThat(result).isEqualTo(15);
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — reduce powinien konkatenować stringi")
    void reduce_shouldConcatenateStrings() {
        String result = BinaryOperatorExercise.reduce(List.of("a", "b", "c"), "", (a, b) -> a + b);

        assertThat(result).isEqualTo("abc");
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — reduce na pustej liście powinien zwrócić identity")
    void reduce_shouldReturnIdentityForEmptyList() {
        int result = BinaryOperatorExercise.reduce(List.of(), 42, Integer::sum);

        assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — reduce z jednym elementem powinien zwrócić ten element")
    void reduce_singleElementReturnsThatElement() {
        int result = BinaryOperatorExercise.reduce(List.of(7), 0, Integer::sum);

        assertThat(result).isEqualTo(7);
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — reduce powinien obliczać iloczyn liczb")
    void reduce_shouldMultiplyNumbers() {
        int result = BinaryOperatorExercise.reduce(List.of(2, 3, 4), 1, (a, b) -> a * b);

        assertThat(result).isEqualTo(24);
    }

    // --- maxBy ---

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — maxBy powinien wybrać większy element z dwóch liczb")
    void maxBy_shouldSelectLargerInteger() {
        Comparator<Integer> comparator = Comparator.naturalOrder();
        BinaryOperator<Integer> max = BinaryOperatorExercise.maxBy(comparator);

        assertThat(max.apply(3, 7)).isEqualTo(7);
        assertThat(max.apply(10, 2)).isEqualTo(10);
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — maxBy powinien wybrać najdłuższy string")
    void maxBy_shouldSelectLongestString() {
        BinaryOperator<String> maxByLength = BinaryOperatorExercise.maxBy(Comparator.comparingInt(String::length));

        assertThat(maxByLength.apply("cat", "elephant")).isEqualTo("elephant");
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — maxBy użyty w reduce powinien znaleźć maksimum na liście")
    void maxBy_usedInReduceShouldFindMaximum() {
        Comparator<Integer> comparator = Comparator.naturalOrder();
        BinaryOperator<Integer> max = BinaryOperatorExercise.maxBy(comparator);
        Integer result = BinaryOperatorExercise.reduce(List.of(3, 1, 4, 1, 5, 9, 2, 6), Integer.MIN_VALUE, max);

        assertThat(result).isEqualTo(9);
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — maxBy gdy oba elementy równe powinien zwrócić jeden z nich")
    void maxBy_equalElementsReturnOneOfThem() {
        Comparator<Integer> comparator = Comparator.naturalOrder();
        BinaryOperator<Integer> max = BinaryOperatorExercise.maxBy(comparator);

        assertThat(max.apply(5, 5)).isEqualTo(5);
    }

    // --- minBy ---

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — minBy powinien wybrać mniejszy element z dwóch liczb")
    void minBy_shouldSelectSmallerInteger() {
        Comparator<Integer> comparator = Comparator.naturalOrder();
        BinaryOperator<Integer> min = BinaryOperatorExercise.minBy(comparator);

        assertThat(min.apply(3, 7)).isEqualTo(3);
        assertThat(min.apply(10, 2)).isEqualTo(2);
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — minBy powinien wybrać najkrótszy string")
    void minBy_shouldSelectShortestString() {
        BinaryOperator<String> minByLength = BinaryOperatorExercise.minBy(Comparator.comparingInt(String::length));

        assertThat(minByLength.apply("cat", "elephant")).isEqualTo("cat");
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — minBy użyty w reduce powinien znaleźć minimum na liście")
    void minBy_usedInReduceShouldFindMinimum() {
        Comparator<Integer> comparator = Comparator.naturalOrder();
        BinaryOperator<Integer> min = BinaryOperatorExercise.minBy(comparator);
        Integer result = BinaryOperatorExercise.reduce(List.of(3, 1, 4, 1, 5, 9, 2, 6), Integer.MAX_VALUE, min);

        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("FEATURE 3: BinaryOperator — minBy na liście stringów według naturalnego porządku")
    void minBy_naturalOrderStrings() {
        Comparator<String> comparator = Comparator.naturalOrder();
        BinaryOperator<String> min = BinaryOperatorExercise.minBy(comparator);
        String result = BinaryOperatorExercise.reduce(List.of("banana", "apple", "cherry"), "zzz", min);

        assertThat(result).isEqualTo("apple");
    }
}
