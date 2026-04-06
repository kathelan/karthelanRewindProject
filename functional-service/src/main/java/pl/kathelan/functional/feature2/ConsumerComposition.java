package pl.kathelan.functional.feature2;

import java.util.List;
import java.util.function.Consumer;

/**
 * Feature 2: Consumer composition exercises.
 * <p>
 * Demonstrates chaining multiple consumers and conditional consumer chains.
 * </p>
 */
public class ConsumerComposition {

    private ConsumerComposition() {
    }

    /**
     * Chains a list of consumers into a single consumer that invokes each one
     * in list order using {@link Consumer#andThen(Consumer)}.
     * <p>
     * An empty list produces a no-op consumer.
     * </p>
     *
     * @param consumers the consumers to chain; must not be null
     * @param <T>       the element type
     * @return a single consumer that executes all consumers sequentially
     */
    public static <T> Consumer<T> chain(List<Consumer<T>> consumers) {
        return consumers.stream()
                .reduce(ignored -> {
                }, Consumer::andThen);
    }

    /**
     * Builds a consumer that applies each {@link ConditionalAction} in order.
     * For each action, the element is passed to the consumer only if the associated
     * predicate returns {@code true}.
     * <p>
     * Multiple actions may fire for a single element — there is no short-circuiting.
     * </p>
     *
     * @param actions the ordered list of condition-action pairs; must not be null
     * @param <T>     the element type
     * @return a composed conditional consumer
     */
    public static <T> Consumer<T> conditionalChain(List<ConditionalAction<T>> actions) {
        return element -> {
            for (ConditionalAction<T> action : actions) {
                if (action.condition().test(element)) {
                    action.action().accept(element);
                }
            }
        };
    }
}
