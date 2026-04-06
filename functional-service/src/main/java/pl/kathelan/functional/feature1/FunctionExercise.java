package pl.kathelan.functional.feature1;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * Feature 1: Function exercises.
 * <p>
 * Demonstrates mapping, function composition, safe parsing, and
 * list-to-map conversion using {@link Function}.
 * </p>
 */
public class FunctionExercise {

    private FunctionExercise() {
    }

    /**
     * Applies {@code mapper} to every element of {@code list} and returns
     * a new list of transformed values.
     *
     * @param list   the source list; must not be null
     * @param mapper the transformation function
     * @param <T>    the input element type
     * @param <R>    the output element type
     * @return a new list with mapped values (same size as input)
     */
    public static <T, R> List<R> transform(List<T> list, Function<T, R> mapper) {
        return list.stream()
                .map(mapper)
                .toList();
    }

    /**
     * Composes {@code first} and {@code second} into a single function that
     * applies {@code first} then {@code second} (left-to-right, i.e. andThen order).
     * <p>
     * Equivalent to {@code first.andThen(second)}.
     * </p>
     *
     * @param first  applied first: T → R
     * @param second applied second: R → V
     * @param <T>    input type
     * @param <R>    intermediate type
     * @param <V>    output type
     * @return a composed function T → V
     */
    public static <T, R, V> Function<T, V> chain(Function<T, R> first, Function<R, V> second) {
        return first.andThen(second);
    }

    /**
     * Tries to apply {@code parser} to {@code value}.
     * Returns {@link Optional#empty()} if an exception is thrown,
     * otherwise wraps the result in an {@link Optional}.
     * <p>
     * Useful for safe conversion of strings to numbers, dates, UUIDs, etc.
     * </p>
     *
     * @param value  the string input to parse
     * @param parser the parsing function that may throw
     * @param <T>    the parsed type
     * @return an Optional containing the parsed value, or empty on failure
     */
    public static <T> Optional<T> tryApply(String value, Function<String, T> parser) {
        try {
            return Optional.ofNullable(parser.apply(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Converts a {@code list} into a {@link Map} where each key is extracted
     * from the element using {@code keyExtractor} and the value is the element itself.
     * <p>
     * Throws {@link IllegalStateException} on duplicate keys (standard Collectors.toMap behaviour).
     * </p>
     *
     * @param list         the source list; must not be null
     * @param keyExtractor function producing the map key from an element
     * @param <T>          the element type
     * @param <K>          the key type
     * @return a Map from key to element
     */
    public static <T, K> Map<K, T> toMap(List<T> list, Function<T, K> keyExtractor) {
        return list.stream()
                .collect(Collectors.toMap(keyExtractor, Function.identity()));
    }
}
