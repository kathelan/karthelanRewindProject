package pl.kathelan.functional.feature1;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Feature 1: Consumer exercises.
 * <p>
 * Demonstrates applying side effects to collections, combining consumers,
 * conditional consumption, and BiConsumer iteration over maps.
 * </p>
 */
public class ConsumerExercise {

    private ConsumerExercise() {
    }

    /**
     * Invokes {@code consumer} for every element in {@code list}.
     * <p>
     * Equivalent to {@code list.forEach(consumer)}, but expressed explicitly
     * to practise passing consumers as parameters.
     * </p>
     *
     * @param list     the source list; must not be null
     * @param consumer the action to apply; must not be null
     * @param <T>      the element type
     */
    public static <T> void applyToAll(List<T> list, Consumer<T> consumer) {
        list.forEach(consumer);
    }

    /**
     * Combines two consumers into a single consumer that invokes {@code first},
     * then {@code second}, for each element.
     * <p>
     * Uses {@link Consumer#andThen(Consumer)} — ordering matters.
     * </p>
     *
     * @param first  the consumer invoked first
     * @param second the consumer invoked second
     * @param <T>    the element type
     * @return a composed consumer
     */
    public static <T> Consumer<T> combine(Consumer<T> first, Consumer<T> second) {
        return first.andThen(second);
    }

    /**
     * Returns a consumer that applies {@code consumer} only when {@code condition}
     * evaluates to {@code true} for the given element.
     * <p>
     * Elements not matching the condition are silently ignored.
     * </p>
     *
     * @param condition the guard predicate
     * @param consumer  the action to apply conditionally
     * @param <T>       the element type
     * @return a conditional consumer
     */
    public static <T> Consumer<T> conditional(Predicate<T> condition, Consumer<T> consumer) {
        return element -> {
            if (condition.test(element)) {
                consumer.accept(element);
            }
        };
    }

    /**
     * Iterates over all entries of {@code map} and passes each key-value pair
     * to {@code consumer}.
     * <p>
     * Demonstrates {@link BiConsumer} as a natural fit for map entry processing.
     * </p>
     *
     * @param map      the map to iterate; must not be null
     * @param consumer the BiConsumer receiving key and value
     * @param <K>      the key type
     * @param <V>      the value type
     */
    public static <K, V> void processMap(Map<K, V> map, BiConsumer<K, V> consumer) {
        map.forEach(consumer);
    }
}
