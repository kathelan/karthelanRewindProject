package pl.kathelan.functional.feature6;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Feature 6: Stream API exercises — static utility methods demonstrating
 * grouping, partitioning, flattening, sorting, collecting, and joining.
 */
public class StreamExercise {

    private StreamExercise() {
    }

    /**
     * Groups words by their first character.
     * Words that are null or empty are silently ignored.
     *
     * @param words list of words to group
     * @return map of first-letter → list of words starting with that letter
     */
    public static Map<Character, List<String>> groupByFirstLetter(List<String> words) {
        return words.stream()
                .filter(w -> w != null && !w.isEmpty())
                .collect(Collectors.groupingBy(w -> w.charAt(0)));
    }

    /**
     * Computes the sum of squares of all even numbers in the list.
     *
     * @param numbers list of integers
     * @return sum of (n*n) for every n where n % 2 == 0
     */
    public static int sumOfEvenSquares(List<Integer> numbers) {
        return numbers.stream()
                .filter(n -> n % 2 == 0)
                .mapToInt(n -> n * n)
                .sum();
    }

    /**
     * Flattens a nested list-of-lists into a single list without duplicates.
     * Encounter order of distinct elements is preserved.
     *
     * @param nested list of sublists
     * @param <T>    element type
     * @return flat, duplicate-free list
     */
    public static <T> List<T> flattenAndDistinct(List<List<T>> nested) {
        return nested.stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    /**
     * Returns the {@code n} largest elements according to {@code comparator}.
     * If the list has fewer than {@code n} elements, all elements are returned.
     *
     * @param list       source list
     * @param comparator ordering; largest elements are those sorted last
     * @param n          maximum number of elements to return
     * @param <T>        element type
     * @return list of up to {@code n} largest elements, in descending order
     */
    public static <T> List<T> topN(List<T> list, Comparator<T> comparator, int n) {
        return list.stream()
                .sorted(comparator.reversed())
                .limit(n)
                .toList();
    }

    /**
     * Partitions words into two groups based on length relative to {@code threshold}.
     *
     * @param words     list of words to partition
     * @param threshold minimum length (inclusive) to be classified as "long"
     * @return map where {@code true} → words with length &ge; threshold,
     *         {@code false} → words with length &lt; threshold
     */
    public static Map<Boolean, List<String>> partitionByLength(List<String> words, int threshold) {
        return words.stream()
                .collect(Collectors.partitioningBy(w -> w.length() >= threshold));
    }

    /**
     * Computes how many times each element appears in the list.
     *
     * @param list source list
     * @param <T>  element type
     * @return map of element → occurrence count
     */
    public static <T> Map<T, Long> frequencyMap(List<T> list) {
        return list.stream()
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()));
    }

    /**
     * Joins a list of words into a single string using {@code separator},
     * preceded by {@code prefix} and followed by {@code suffix}.
     *
     * @param words     list of words to join
     * @param separator string placed between consecutive words
     * @param prefix    string placed before the first word
     * @param suffix    string placed after the last word
     * @return formatted joined string
     */
    public static String joinWithSeparator(List<String> words, String separator,
                                           String prefix, String suffix) {
        return words.stream()
                .collect(Collectors.joining(separator, prefix, suffix));
    }
}
