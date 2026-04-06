package pl.kathelan.functional.feature6;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.kathelan.functional.feature6.model.Product;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StreamExerciseTest {

    // -----------------------------------------------------------------------
    // groupByFirstLetter
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Stream — groupByFirstLetter grupuje słowa po pierwszej literze")
    void groupByFirstLetterGroupsCorrectly() {
        List<String> words = List.of("apple", "avocado", "banana", "blueberry", "cherry");

        Map<Character, List<String>> result = StreamExercise.groupByFirstLetter(words);

        assertThat(result).containsOnlyKeys('a', 'b', 'c');
        assertThat(result.get('a')).containsExactlyInAnyOrder("apple", "avocado");
        assertThat(result.get('b')).containsExactlyInAnyOrder("banana", "blueberry");
        assertThat(result.get('c')).containsExactly("cherry");
    }

    @Test
    @DisplayName("FEATURE 6: Stream — groupByFirstLetter zwraca pustą mapę dla pustej listy")
    void groupByFirstLetterReturnsEmptyMapForEmptyInput() {
        Map<Character, List<String>> result = StreamExercise.groupByFirstLetter(List.of());

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // sumOfEvenSquares
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Stream — sumOfEvenSquares oblicza sumę kwadratów liczb parzystych")
    void sumOfEvenSquaresCalculatesCorrectly() {
        // even: 2, 4, 6 → 4 + 16 + 36 = 56
        int result = StreamExercise.sumOfEvenSquares(List.of(1, 2, 3, 4, 5, 6));

        assertThat(result).isEqualTo(56);
    }

    @Test
    @DisplayName("FEATURE 6: Stream — sumOfEvenSquares zwraca 0 gdy brak liczb parzystych")
    void sumOfEvenSquaresReturnsZeroWhenNoEvenNumbers() {
        int result = StreamExercise.sumOfEvenSquares(List.of(1, 3, 5, 7));

        assertThat(result).isZero();
    }

    // -----------------------------------------------------------------------
    // flattenAndDistinct
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Stream — flattenAndDistinct usuwa duplikaty z zagnieżdżonych list")
    void flattenAndDistinctRemovesDuplicates() {
        List<List<Integer>> nested = List.of(
                List.of(1, 2, 3),
                List.of(2, 3, 4),
                List.of(4, 5)
        );

        List<Integer> result = StreamExercise.flattenAndDistinct(nested);

        assertThat(result).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("FEATURE 6: Stream — flattenAndDistinct zwraca pustą listę dla pustego wejścia")
    void flattenAndDistinctReturnsEmptyForEmptyInput() {
        List<Integer> result = StreamExercise.flattenAndDistinct(List.of());

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // topN
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Stream — topN zwraca N największych elementów")
    void topNReturnsNLargestElements() {
        List<Integer> result = StreamExercise.topN(
                List.of(3, 1, 4, 1, 5, 9, 2, 6),
                Comparator.naturalOrder(),
                3
        );

        assertThat(result).containsExactly(9, 6, 5);
    }

    @Test
    @DisplayName("FEATURE 6: Stream — topN zwraca wszystkie gdy lista krótsza niż N")
    void topNReturnsAllWhenListSmallerThanN() {
        List<Integer> result = StreamExercise.topN(
                List.of(7, 2),
                Comparator.naturalOrder(),
                10
        );

        assertThat(result).containsExactly(7, 2);
    }

    // -----------------------------------------------------------------------
    // partitionByLength
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Stream — partitionByLength dzieli słowa na długie i krótkie")
    void partitionByLengthDividesCorrectly() {
        List<String> words = List.of("hi", "hello", "world", "go", "java");

        Map<Boolean, List<String>> result = StreamExercise.partitionByLength(words, 4);

        assertThat(result.get(true)).containsExactlyInAnyOrder("hello", "world", "java");
        assertThat(result.get(false)).containsExactlyInAnyOrder("hi", "go");
    }

    // -----------------------------------------------------------------------
    // frequencyMap
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Stream — frequencyMap liczy wystąpienia każdego elementu")
    void frequencyMapCountsOccurrences() {
        List<String> items = List.of("a", "b", "a", "c", "b", "a");

        Map<String, Long> result = StreamExercise.frequencyMap(items);

        assertThat(result).containsEntry("a", 3L)
                .containsEntry("b", 2L)
                .containsEntry("c", 1L);
    }

    @Test
    @DisplayName("FEATURE 6: Stream — frequencyMap zwraca pustą mapę dla pustej listy")
    void frequencyMapReturnsEmptyForEmptyList() {
        Map<String, Long> result = StreamExercise.frequencyMap(List.of());

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // joinWithSeparator
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Stream — joinWithSeparator łączy słowa z separatorem, prefiksem i sufiksem")
    void joinWithSeparatorProducesExpectedString() {
        String result = StreamExercise.joinWithSeparator(
                List.of("Java", "Spring", "Boot"),
                ", ", "[", "]"
        );

        assertThat(result).isEqualTo("[Java, Spring, Boot]");
    }

    // -----------------------------------------------------------------------
    // Product model sanity check
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("FEATURE 6: Stream — topN z Comparator po cenie zwraca najdroższe produkty")
    void topNWithProductComparatorReturnsMostExpensive() {
        List<Product> products = List.of(
                Product.builder().name("Laptop").category("Electronics").price(3000.0).build(),
                Product.builder().name("Mouse").category("Electronics").price(50.0).build(),
                Product.builder().name("Desk").category("Furniture").price(800.0).build(),
                Product.builder().name("Chair").category("Furniture").price(500.0).build()
        );

        List<Product> top2 = StreamExercise.topN(products, Comparator.comparingDouble(Product::getPrice), 2);

        assertThat(top2).extracting(Product::getName).containsExactly("Laptop", "Desk");
    }
}
