package pl.kathelan.functional.feature1;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Feature 1: Supplier exercises.
 * <p>
 * Demonstrates lazy evaluation, default value resolution, and list generation
 * using the {@link Supplier} functional interface.
 * </p>
 */
public class SupplierExercise {

    private SupplierExercise() {
    }

    /**
     * Returns a memoizing (lazy) Supplier that computes the value only once
     * and caches it for all subsequent calls.
     * <p>
     * Useful when the computation is expensive and the result is immutable.
     * </p>
     *
     * @param supplier the underlying supplier whose result will be cached
     * @param <T>      the type of result
     * @return a supplier that calls the original at most once
     */
    public static <T> Supplier<T> lazy(Supplier<T> supplier) {
        Object[] holder = new Object[1];
        boolean[] computed = new boolean[1];
        return () -> {
            synchronized (holder) {
                if (!computed[0]) {
                    holder[0] = supplier.get();
                    computed[0] = true;
                }
                return (T) holder[0];
            }
        };
    }

    /**
     * Returns {@code value} if it is not null, otherwise obtains a default value
     * from {@code defaultSupplier}.
     * <p>
     * The supplier is only invoked when {@code value} is null — true lazy default.
     * </p>
     *
     * @param value           the value to check
     * @param defaultSupplier supplier providing the fallback value
     * @param <T>             the type of value
     * @return {@code value} when non-null, otherwise the supplier's result
     */
    public static <T> T getOrDefault(T value, Supplier<T> defaultSupplier) {
        return value != null ? value : defaultSupplier.get();
    }

    /**
     * Generates a {@link List} of {@code count} elements by invoking {@code supplier}
     * once per element.
     * <p>
     * Each call to the supplier may produce a different value, making this suitable
     * for random-data generation, UUID generation, etc.
     * </p>
     *
     * @param count    the number of elements to generate; must be &ge; 0
     * @param supplier the element factory
     * @param <T>      the element type
     * @return a mutable list containing {@code count} supplied values
     */
    public static <T> List<T> generate(int count, Supplier<T> supplier) {
        List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(supplier.get());
        }
        return result;
    }
}
