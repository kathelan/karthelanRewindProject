package pl.kathelan.functional.feature2;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A pair of a guard {@link Predicate} and a {@link Consumer} to execute
 * when the predicate holds.
 * <p>
 * Used by {@link ConsumerComposition#conditionalChain(java.util.List)}.
 * </p>
 *
 * @param condition the guard evaluated before the action
 * @param action    the side effect executed when condition is true
 * @param <T>       the element type
 */
public record ConditionalAction<T>(Predicate<T> condition, Consumer<T> action) {
}
