package pl.kathelan.functional.feature2;

import java.util.List;
import java.util.function.Predicate;

/**
 * Feature 2: Predicate composition exercises.
 * <p>
 * Demonstrates negation and logical combination (AND, OR, NONE) of predicates.
 * </p>
 */
public class PredicateComposition {

    private PredicateComposition() {
    }

    /**
     * Returns the logical negation of {@code predicate}.
     * <p>
     * Thin wrapper around {@link Predicate#not(Predicate)} to make intent explicit
     * at the call site.
     * </p>
     *
     * @param predicate the predicate to negate; must not be null
     * @param <T>       the element type
     * @return a predicate that returns {@code true} when {@code predicate} returns {@code false}
     */
    public static <T> Predicate<T> not(Predicate<T> predicate) {
        return Predicate.not(predicate);
    }

    /**
     * Combines a list of predicates with logical AND.
     * An element passes only if <em>all</em> predicates return {@code true}.
     * <p>
     * An empty list yields a predicate that always returns {@code true} (vacuous truth).
     * </p>
     *
     * @param predicates the predicates to combine; must not be null
     * @param <T>        the element type
     * @return combined AND predicate
     */
    public static <T> Predicate<T> allMatch(List<Predicate<T>> predicates) {
        return predicates.stream()
                .reduce(p -> true, Predicate::and);
    }

    /**
     * Combines a list of predicates with logical OR.
     * An element passes if <em>at least one</em> predicate returns {@code true}.
     * <p>
     * An empty list yields a predicate that always returns {@code false}.
     * </p>
     *
     * @param predicates the predicates to combine; must not be null
     * @param <T>        the element type
     * @return combined OR predicate
     */
    public static <T> Predicate<T> anyMatch(List<Predicate<T>> predicates) {
        return predicates.stream()
                .reduce(p -> false, Predicate::or);
    }

    /**
     * Returns a predicate that passes only when <em>none</em> of the given
     * predicates match.
     * <p>
     * Equivalent to negating the OR of all predicates.
     * An empty list yields a predicate that always returns {@code true}.
     * </p>
     *
     * @param predicates the predicates to check against; must not be null
     * @param <T>        the element type
     * @return a noneMatch predicate
     */
    public static <T> Predicate<T> noneMatch(List<Predicate<T>> predicates) {
        return anyMatch(predicates).negate();
    }
}
