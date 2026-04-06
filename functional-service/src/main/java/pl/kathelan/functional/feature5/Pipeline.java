package pl.kathelan.functional.feature5;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Feature 5: Immutable mini-stream pipeline built on {@link Function} and {@link Predicate}.
 * <p>
 * Each {@link #map}, {@link #mapTo}, and {@link #filter} call returns a <em>new</em>
 * {@code Pipeline} instance — the original is never modified. Execution is eager and
 * performed only when {@link #execute} or {@link #executeAll} is called.
 * </p>
 *
 * <p>Steps are stored as type-erased {@code Function<Object, Object>} and
 * {@code Predicate<Object>} internally, which allows {@link #mapTo} to change
 * the result type while keeping a single, unified step list. The public API
 * remains type-safe: type parameters on {@code Pipeline<T>} ensure callers
 * pass correct input types and receive correct output types.</p>
 *
 * <pre>{@code
 * Optional<String> result = Pipeline.<String>of()
 *     .map(String::trim)
 *     .map(String::toUpperCase)
 *     .filter(s -> !s.isEmpty())
 *     .map(s -> "[" + s + "]")
 *     .execute("  hello  ");
 * // → Optional.of("[HELLO]")
 * }</pre>
 *
 * @param <I> the original input type (fixed at creation — used by execute/executeAll)
 * @param <O> the current output type (may change via mapTo)
 */
public class Pipeline<I, O> {

    private enum StepKind { MAP, FILTER }

    @SuppressWarnings("rawtypes")
    private record Step(StepKind kind, Function mapper, Predicate predicate) {

        static Step map(Function<?, ?> mapper) {
            return new Step(StepKind.MAP, mapper, null);
        }

        static Step filter(Predicate<?> predicate) {
            return new Step(StepKind.FILTER, null, predicate);
        }
    }

    private final List<Step> steps;

    private Pipeline(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    /**
     * Creates a new, empty pipeline where input and output type are the same ({@code T}).
     *
     * @param <T> element type
     * @return empty pipeline
     */
    public static <T> Pipeline<T, T> of() {
        return new Pipeline<>(List.of());
    }

    /**
     * Returns a new pipeline with a map step appended (same output type {@code O}).
     *
     * @param mapper function applied to the current output value
     * @return new pipeline instance
     */
    public Pipeline<I, O> map(Function<O, O> mapper) {
        List<Step> updated = new ArrayList<>(steps);
        updated.add(Step.map(mapper));
        return new Pipeline<>(updated);
    }

    /**
     * Returns a new pipeline of type {@code R} by appending a type-changing map step.
     *
     * @param mapper function that converts current output type {@code O} to {@code R}
     * @param <R>    the new output type
     * @return new pipeline of type {@code Pipeline<I, R>}
     */
    public <R> Pipeline<I, R> mapTo(Function<O, R> mapper) {
        List<Step> updated = new ArrayList<>(steps);
        updated.add(Step.map(mapper));
        return new Pipeline<>(updated);
    }

    /**
     * Returns a new pipeline with a filter step appended.
     * When the predicate returns {@code false}, {@link #execute} returns
     * {@link Optional#empty()} and {@link #executeAll} skips the element.
     *
     * @param predicate condition that must hold for the current output value to pass
     * @return new pipeline instance
     */
    public Pipeline<I, O> filter(Predicate<O> predicate) {
        List<Step> updated = new ArrayList<>(steps);
        updated.add(Step.filter(predicate));
        return new Pipeline<>(updated);
    }

    /**
     * Runs all pipeline steps against a single input value.
     *
     * @param input the original input value
     * @return {@link Optional} containing the processed output, or empty if filtered out
     */
    public Optional<O> execute(I input) {
        return applySteps(input);
    }

    /**
     * Runs all pipeline steps against every element of {@code inputs}.
     * Elements that are filtered out are silently dropped.
     *
     * @param inputs list of original input values
     * @return list of processed outputs that passed all steps (in original order)
     */
    public List<O> executeAll(List<I> inputs) {
        List<O> results = new ArrayList<>();
        for (I input : inputs) {
            applySteps(input).ifPresent(results::add);
        }
        return results;
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Optional<O> applySteps(I input) {
        Object current = input;
        for (Step step : steps) {
            if (current == null) {
                return Optional.empty();
            }
            if (step.kind() == StepKind.MAP) {
                current = step.mapper().apply(current);
            } else {
                if (!step.predicate().test(current)) {
                    return Optional.empty();
                }
            }
        }
        return current == null ? Optional.empty() : Optional.of((O) current);
    }
}
