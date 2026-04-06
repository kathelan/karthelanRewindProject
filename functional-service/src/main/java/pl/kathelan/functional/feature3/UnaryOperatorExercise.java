package pl.kathelan.functional.feature3;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Feature 3: UnaryOperator exercises.
 * <p>
 * Demonstrates identity, operator chaining, repeated application,
 * and conditional operators.
 * </p>
 */
public class UnaryOperatorExercise {

    private UnaryOperatorExercise() {
    }

    /**
     * Returns the identity {@link UnaryOperator} — always returns its input unchanged.
     * <p>
     * Wraps {@link UnaryOperator#identity()} to make the concept explicit at practice sites.
     * </p>
     *
     * @param <T> the element type
     * @return identity operator
     */
    public static <T> UnaryOperator<T> identity() {
        return UnaryOperator.identity();
    }

    /**
     * Chains multiple {@link UnaryOperator}s into a single operator that applies
     * them in list order (left to right).
     * <p>
     * Uses {@link java.util.function.Function#andThen(java.util.function.Function)} internally.
     * An empty list produces the identity operator.
     * </p>
     *
     * @param operators the ordered list of operators to chain; must not be null
     * @param <T>       the element type
     * @return a single UnaryOperator representing the chain
     */
    public static <T> UnaryOperator<T> chain(List<UnaryOperator<T>> operators) {
        return operators.stream()
                .<UnaryOperator<T>>reduce(UnaryOperator.identity(),
                        (acc, op) -> value -> op.apply(acc.apply(value)));
    }

    /**
     * Applies {@code operator} exactly {@code times} times to {@code value}.
     * <p>
     * When {@code times} is 0, the original value is returned unchanged.
     * </p>
     *
     * @param value    the initial value
     * @param operator the operator to apply repeatedly
     * @param times    the number of applications; must be &ge; 0
     * @param <T>      the element type
     * @return the value after {@code times} applications of the operator
     */
    public static <T> T applyN(T value, UnaryOperator<T> operator, int times) {
        T result = value;
        for (int i = 0; i < times; i++) {
            result = operator.apply(result);
        }
        return result;
    }

    /**
     * Returns an operator that applies {@code operator} only when {@code condition}
     * holds for the current value; otherwise the value is returned unchanged.
     *
     * @param condition the guard predicate evaluated before each application
     * @param operator  the operator to apply conditionally
     * @param <T>       the element type
     * @return a conditional UnaryOperator
     */
    public static <T> UnaryOperator<T> conditional(Predicate<T> condition, UnaryOperator<T> operator) {
        return value -> condition.test(value) ? operator.apply(value) : value;
    }
}
