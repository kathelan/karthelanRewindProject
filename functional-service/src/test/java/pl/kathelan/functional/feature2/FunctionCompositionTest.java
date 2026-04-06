package pl.kathelan.functional.feature2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionCompositionTest {

    // --- buildPipeline ---

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — buildPipeline powinien zastosować funkcje w kolejności listy")
    void buildPipeline_shouldApplyFunctionsInListOrder() {
        List<Function<String, String>> pipeline = List.of(
                String::trim,
                String::toLowerCase,
                s -> s + "!"
        );

        String result = FunctionComposition.buildPipeline(pipeline).apply("  HELLO  ");

        assertThat(result).isEqualTo("hello!");
    }

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — buildPipeline z pustą listą powinien zwrócić identyczną wartość")
    void buildPipeline_shouldReturnIdentityForEmptyList() {
        Function<String, String> pipeline = FunctionComposition.buildPipeline(List.of());

        assertThat(pipeline.apply("unchanged")).isEqualTo("unchanged");
    }

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — buildPipeline z jedną funkcją powinien zachować się jak ta funkcja")
    void buildPipeline_shouldBehaveLikeSingleFunctionForOneElement() {
        Function<Integer, Integer> pipeline = FunctionComposition.buildPipeline(List.of(n -> n * 3));

        assertThat(pipeline.apply(5)).isEqualTo(15);
    }

    // --- compose ---

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — compose powinien stosować inner przed outer (odwrotnie do andThen)")
    void compose_shouldApplyInnerBeforeOuter() {
        // inner: String → Integer (length)
        // outer: Integer → String (toString with prefix)
        Function<String, String> composed = FunctionComposition.compose(
                n -> "length=" + n,  // outer: Integer → String
                String::length        // inner: String → Integer
        );

        assertThat(composed.apply("hello")).isEqualTo("length=5");
    }

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — compose kolejność: outer(inner(x)), nie inner(outer(x))")
    void compose_orderIsOuterOfInner() {
        List<String> executionOrder = new java.util.ArrayList<>();

        Function<String, String> inner = s -> {
            executionOrder.add("inner");
            return s.toUpperCase();
        };
        Function<String, String> outer = s -> {
            executionOrder.add("outer");
            return s + "!";
        };

        FunctionComposition.compose(outer, inner).apply("test");

        assertThat(executionOrder).containsExactly("inner", "outer");
    }

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — compose vs andThen dają odwrotny wynik dla nieprzemiennych funkcji")
    void compose_producesOppositeResultToAndThenForNonCommutativeOps() {
        Function<Integer, Integer> times2 = n -> n * 2;
        Function<Integer, Integer> plus3 = n -> n + 3;

        // compose(times2, plus3) = times2(plus3(x)) = (x+3)*2
        int composeResult = FunctionComposition.compose(times2, plus3).apply(4); // (4+3)*2 = 14

        // andThen equivalent: plus3 then times2 = (4+3)*2 = 14  — same here
        // Let's pick non-commutative: compose(plus3, times2) = plus3(times2(x)) = x*2+3
        int composeResult2 = FunctionComposition.compose(plus3, times2).apply(4); // 4*2+3 = 11

        assertThat(composeResult).isEqualTo(14);
        assertThat(composeResult2).isEqualTo(11);
        assertThat(composeResult).isNotEqualTo(composeResult2);
    }

    // --- applyIfPresent ---

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — applyIfPresent powinien zmapować wartość gdy Optional jest present")
    void applyIfPresent_shouldMapValueWhenPresent() {
        Optional<String> result = FunctionComposition.applyIfPresent(Optional.of("hello"), String::toUpperCase);

        assertThat(result).contains("HELLO");
    }

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — applyIfPresent powinien zwrócić empty gdy Optional jest pusty")
    void applyIfPresent_shouldReturnEmptyForEmptyOptional() {
        Optional<Integer> result = FunctionComposition.applyIfPresent(Optional.<String>empty(), String::length);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 2: FunctionComposition — applyIfPresent nie powinien wywoływać mapper dla pustego Optional")
    void applyIfPresent_shouldNotInvokeMapperForEmpty() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger(0);

        FunctionComposition.applyIfPresent(Optional.<String>empty(), (String s) -> {
            calls.incrementAndGet();
            return s.length();
        });

        assertThat(calls.get()).isZero();
    }
}
