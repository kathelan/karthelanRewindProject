package pl.kathelan.functional.feature3;

import java.util.Comparator;
import java.util.List;
import java.util.function.BinaryOperator;

/**
 * Feature 3: BinaryOperator exercises.
 * <p>
 * Demonstrates reduction, and selection of the larger or smaller of two values.
 * </p>
 */
public class BinaryOperatorExercise {

    private BinaryOperatorExercise() {
    }

    /**
     * Reduces {@code list} to a single value using the provided {@code identity}
     * element and {@code operator}.
     * <p>
     * For an empty list the identity is returned unchanged, making the result
     * well-defined for any input.
     * </p>
     *
     * @param list     the source list; must not be null
     * @param identity the neutral element for the operation (0 for sum, "" for concat, etc.)
     * @param operator the accumulation function
     * @param <T>      the element type
     * @return the reduced value
     */
    public static <T> T reduce(List<T> list, T identity, BinaryOperator<T> operator) {
        return list.stream()
                .reduce(identity, operator);
    }

    /**
     * Returns a {@link BinaryOperator} that selects the <em>greater</em> of its two
     * arguments according to {@code comparator}.
     * <p>
     * Thin wrapper around {@link BinaryOperator#maxBy(Comparator)} to make
     * the concept explicit.
     * </p>
     *
     * @param comparator the ordering criterion; must not be null
     * @param <T>        the element type
     * @return a BinaryOperator that returns the larger element
     */
    public static <T> BinaryOperator<T> maxBy(Comparator<T> comparator) {
        return BinaryOperator.maxBy(comparator);
    }

    /**
     * Returns a {@link BinaryOperator} that selects the <em>smaller</em> of its two
     * arguments according to {@code comparator}.
     * <p>
     * Thin wrapper around {@link BinaryOperator#minBy(Comparator)}.
     * </p>
     *
     * @param comparator the ordering criterion; must not be null
     * @param <T>        the element type
     * @return a BinaryOperator that returns the smaller element
     */
    public static <T> BinaryOperator<T> minBy(Comparator<T> comparator) {
        return BinaryOperator.minBy(comparator);
    }
}
