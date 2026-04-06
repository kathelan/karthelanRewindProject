package pl.kathelan.functional.feature1;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * Feature 1: Predicate exercises.
 * <p>
 * Demonstrates filtering, counting, combining predicates with AND/OR logic,
 * and partitioning a collection into two groups.
 * </p>
 */
public class PredicateExercise {

    private PredicateExercise() {
    }

    /**
     * Returns a new list containing only those elements for which
     * {@code predicate} returns {@code true}.
     *
     * @param list      the source list; must not be null
     * @param predicate the filter criterion
     * @param <T>       the element type
     * @return a new, possibly empty list of matching elements
     */
    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        return list.stream()
                .filter(predicate)
                .toList();
    }

    /**
     * Counts the number of elements in {@code list} for which {@code predicate}
     * returns {@code true}.
     *
     * @param list      the source list; must not be null
     * @param predicate the counting criterion
     * @param <T>       the element type
     * @return the count of matching elements
     */
    public static <T> long count(List<T> list, Predicate<T> predicate) {
        return list.stream()
                .filter(predicate)
                .count();
    }

    /**
     * Combines a list of predicates with logical AND.
     * An element passes only if <em>all</em> predicates match.
     * <p>
     * When {@code predicates} is empty, returns a predicate that always returns {@code true}
     * (vacuous truth — consistent with {@link Predicate#and} semantics).
     * </p>
     *
     * @param predicates the predicates to combine; must not be null
     * @param <T>        the element type
     * @return a combined AND predicate
     */
    public static <T> Predicate<T> allOf(List<Predicate<T>> predicates) {
        return predicates.stream()
                .reduce(p -> true, Predicate::and);
    }

    /**
     * Combines a list of predicates with logical OR.
     * An element passes if <em>any</em> predicate matches.
     * <p>
     * When {@code predicates} is empty, returns a predicate that always returns {@code false}
     * (consistent with stream anyMatch on an empty list).
     * </p>
     *
     * @param predicates the predicates to combine; must not be null
     * @param <T>        the element type
     * @return a combined OR predicate
     */
    public static <T> Predicate<T> anyOf(List<Predicate<T>> predicates) {
        return predicates.stream()
                .reduce(p -> false, Predicate::or);
    }

    /**
     * Partitions {@code list} into two groups based on {@code predicate}.
     * <p>
     * Returns a {@link Map} with:
     * <ul>
     *   <li>{@code true} → elements matching the predicate</li>
     *   <li>{@code false} → elements not matching the predicate</li>
     * </ul>
     * Both keys are always present (the lists may be empty).
     * </p>
     *
     * @param list      the source list; must not be null
     * @param predicate the partitioning criterion
     * @param <T>       the element type
     * @return a Map with exactly two entries keyed by {@code true} and {@code false}
     */
    public static <T> Map<Boolean, List<T>> partition(List<T> list, Predicate<T> predicate) {
        return list.stream()
                .collect(Collectors.partitioningBy(predicate));
    }
}
