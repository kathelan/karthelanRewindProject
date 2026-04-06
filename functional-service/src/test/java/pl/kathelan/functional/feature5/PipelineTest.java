package pl.kathelan.functional.feature5;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineTest {

    @Test
    @DisplayName("FEATURE 5: Pipeline — map transformuje wartość krok po kroku")
    void mapTransformsValueStepByStep() {
        Optional<String> result = Pipeline.<String>of()
                .map(String::trim)
                .map(String::toUpperCase)
                .map(s -> "[" + s + "]")
                .execute("  hello  ");

        assertThat(result).hasValue("[HELLO]");
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — filter zwraca empty gdy warunek nie spełniony")
    void filterReturnsEmptyWhenConditionNotMet() {
        Optional<String> result = Pipeline.<String>of()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .execute("   ");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — filter przepuszcza wartość gdy warunek spełniony")
    void filterPassesThroughWhenConditionMet() {
        Optional<String> result = Pipeline.<String>of()
                .filter(s -> s.startsWith("J"))
                .execute("Java");

        assertThat(result).hasValue("Java");
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — executeAll filtruje i transformuje listę")
    void executeAllFiltersAndTransformsList() {
        List<String> result = Pipeline.<String>of()
                .map(String::trim)
                .filter(s -> s.length() > 3)
                .map(String::toUpperCase)
                .executeAll(List.of("hi", "hello", "  world  ", "ok"));

        assertThat(result).containsExactly("HELLO", "WORLD");
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — pusta lista zwraca pustą listę")
    void executeAllOnEmptyListReturnsEmptyList() {
        List<String> result = Pipeline.<String>of()
                .map(String::toUpperCase)
                .executeAll(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — mapTo zmienia typ (String → Integer)")
    void mapToChangesType() {
        Optional<Integer> result = Pipeline.<String>of()
                .map(String::trim)
                .mapTo(String::length)
                .execute("  hello  ");

        assertThat(result).hasValue(5);
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — pipeline jest immutable (map zwraca nowy obiekt)")
    void mapReturnsDifferentPipelineInstance() {
        Pipeline<String, String> original = Pipeline.of();
        Pipeline<String, String> withStep = original.map(String::toUpperCase);

        assertThat(original).isNotSameAs(withStep);
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — pipeline jest immutable (filter zwraca nowy obiekt)")
    void filterReturnsDifferentPipelineInstance() {
        Pipeline<String, String> original = Pipeline.of();
        Pipeline<String, String> withFilter = original.filter(s -> !s.isEmpty());

        assertThat(original).isNotSameAs(withFilter);
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — wielokrotny filter kumuluje warunki")
    void multipleFiltersAccumulateConditions() {
        Optional<String> result = Pipeline.<String>of()
                .filter(s -> s.length() > 2)
                .filter(s -> s.startsWith("A"))
                .execute("Al");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 5: Pipeline — pusty pipeline nie zmienia wartości")
    void emptyPipelinePassesThroughValue() {
        Optional<String> result = Pipeline.<String>of().execute("unchanged");

        assertThat(result).hasValue("unchanged");
    }
}
