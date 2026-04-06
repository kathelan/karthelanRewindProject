package pl.kathelan.functional.feature2;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Feature 2: Function composition exercises.
 * <p>
 * Demonstrates building transformation pipelines, mathematical function
 * composition (f∘g), and safe Optional mapping.
 * </p>
 */
public class FunctionComposition {

    private FunctionComposition() {
    }

    /**
     * Builds a pipeline from a list of same-type transformation functions.
     * Functions are applied in list order (left to right), equivalent to
     * chaining multiple {@link Function#andThen(Function)} calls.
     * <p>
     * An empty list produces the identity function.
     * </p>
     *
     * @param functions the ordered list of transformations; must not be null
     * @param <T>       the element type (input and output are the same)
     * @return a single function representing the full pipeline
     */
    public static <T> Function<T, T> buildPipeline(List<Function<T, T>> functions) {
        return functions.stream()
                .reduce(Function.identity(), Function::andThen);
    }

    /**
     * Composes {@code outer} and {@code inner} in mathematical order: outer(inner(x)).
     * <p>
     * This is the reverse of {@link Function#andThen}: here {@code inner} runs first,
     * then {@code outer} is applied to the result.
     * Equivalent to {@code inner.andThen(outer)} or {@code outer.compose(inner)}.
     * </p>
     * <p>
     * <strong>Gotcha:</strong> {@code compose} and {@code andThen} are easily confused.
     * {@code f.compose(g)} = g then f; {@code f.andThen(g)} = f then g.
     * </p>
     *
     * @param outer applied second: B → C
     * @param inner applied first:  A → B
     * @param <A>   input type
     * @param <B>   intermediate type
     * @param <C>   output type
     * @return composed function A → C
     */
    public static <A, B, C> Function<A, C> compose(Function<B, C> outer, Function<A, B> inner) {
        return outer.compose(inner);
    }

    /**
     * Applies {@code mapper} to the value inside {@code optional}, if present.
     * Returns {@link Optional#empty()} when the optional is empty.
     * <p>
     * Equivalent to {@code optional.map(mapper)}, but expressed as a utility
     * to practise passing Functions alongside Optionals.
     * </p>
     *
     * @param optional the source optional; must not be null
     * @param mapper   the transformation to apply if the value is present
     * @param <T>      the source type
     * @param <R>      the result type
     * @return an Optional containing the mapped value, or empty
     */
    public static <T, R> Optional<R> applyIfPresent(Optional<T> optional, Function<T, R> mapper) {
        return optional.map(mapper);
    }
}
