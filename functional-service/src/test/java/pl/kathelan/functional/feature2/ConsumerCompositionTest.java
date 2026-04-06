package pl.kathelan.functional.feature2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumerCompositionTest {

    // --- chain ---

    @Test
    @DisplayName("FEATURE 2: ConsumerComposition — chain powinien wywołać wszystkich consumerów po kolei")
    void chain_shouldInvokeAllConsumersInOrder() {
        List<String> order = new ArrayList<>();

        Consumer<String> chained = ConsumerComposition.chain(List.of(
                s -> order.add("first:" + s),
                s -> order.add("second:" + s),
                s -> order.add("third:" + s)
        ));

        chained.accept("x");

        assertThat(order).containsExactly("first:x", "second:x", "third:x");
    }

    @Test
    @DisplayName("FEATURE 2: ConsumerComposition — chain z pustą listą powinien być no-op")
    void chain_shouldBeNoOpForEmptyList() {
        List<String> collected = new ArrayList<>();
        Consumer<String> chained = ConsumerComposition.chain(List.of());

        chained.accept("value");

        assertThat(collected).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 2: ConsumerComposition — chain z jednym consumerem powinien zachować się jak ten consumer")
    void chain_withSingleConsumerBehavesLikeThatConsumer() {
        List<Integer> collected = new ArrayList<>();
        Consumer<Integer> chained = ConsumerComposition.chain(List.of(collected::add));

        chained.accept(99);

        assertThat(collected).containsExactly(99);
    }

    // --- conditionalChain ---

    @Test
    @DisplayName("FEATURE 2: ConsumerComposition — conditionalChain powinien wywołać tylko consumer z pasującym warunkiem")
    void conditionalChain_shouldInvokeOnlyMatchingConsumers() {
        List<String> collected = new ArrayList<>();

        Consumer<Integer> consumer = ConsumerComposition.conditionalChain(List.of(
                new ConditionalAction<>(n -> n > 0, n -> collected.add("positive:" + n)),
                new ConditionalAction<>(n -> n < 0, n -> collected.add("negative:" + n)),
                new ConditionalAction<>(n -> n % 2 == 0, n -> collected.add("even:" + n))
        ));

        consumer.accept(4); // positive and even

        assertThat(collected).containsExactly("positive:4", "even:4");
    }

    @Test
    @DisplayName("FEATURE 2: ConsumerComposition — conditionalChain gdy żaden warunek nie pasuje nie wywołuje żadnego")
    void conditionalChain_shouldNotInvokeAnyWhenNoneMatch() {
        List<String> collected = new ArrayList<>();

        Consumer<Integer> consumer = ConsumerComposition.conditionalChain(List.of(
                new ConditionalAction<>(n -> n > 100, n -> collected.add("big")),
                new ConditionalAction<>(n -> n < 0, n -> collected.add("negative"))
        ));

        consumer.accept(50);

        assertThat(collected).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 2: ConsumerComposition — conditionalChain z pustą listą nie wywołuje nic")
    void conditionalChain_shouldDoNothingForEmptyActions() {
        List<String> collected = new ArrayList<>();
        Consumer<String> consumer = ConsumerComposition.conditionalChain(List.of());

        consumer.accept("test");

        assertThat(collected).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 2: ConsumerComposition — conditionalChain może wywołać wiele consumerów dla jednego elementu")
    void conditionalChain_canFireMultipleActionsForSingleElement() {
        List<String> fired = new ArrayList<>();

        Consumer<String> consumer = ConsumerComposition.conditionalChain(List.of(
                new ConditionalAction<>(s -> s.length() > 2, s -> fired.add("length>2")),
                new ConditionalAction<>(s -> s.startsWith("H"), s -> fired.add("startsH")),
                new ConditionalAction<>(s -> s.endsWith("o"), s -> fired.add("endsO"))
        ));

        consumer.accept("Hello");

        assertThat(fired).containsExactly("length>2", "startsH", "endsO");
    }
}
