package pl.kathelan.common.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CountBasedCircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.circuitbreaker.State;
import pl.kathelan.common.resilience.exception.CircuitOpenException;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ResilientCaller łączy Circuit Breaker z Retry w jeden mechanizm odporności.
 *
 * Kluczowa zasada: CB owija Retry, a NIE odwrotnie.
 *
 *   CB.execute(
 *       () -> Retry.execute(supplier)
 *   )
 *
 * Dlaczego taka kolejność?
 *   - Retry obsługuje chwilowe błędy (timeout, krótka niedostępność)
 *   - CB liczy 1 failure DOPIERO gdy Retry wyczerpie wszystkie próby
 *   - Gdyby było odwrotnie (Retry owija CB), każda retry-próba otwierałaby CB
 *     po pierwszym błędzie — CB byłby bezużyteczny
 *
 * W testach: maxAttempts=3 (Retry), threshold=3 (CB), delay=0ms (szybkie testy)
 */
@DisplayName("ResilientCaller — Circuit Breaker + Retry")
class ResilientCallerTest {

    private static final String SERVICE = "zewnętrzny-serwis";
    private static final int CB_THRESHOLD = 3;

    private InMemoryCircuitBreakerStateRepository repository;
    private ResilientCaller caller;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCircuitBreakerStateRepository();
        CircuitBreakerConfig cbConfig = new CircuitBreakerConfig(CB_THRESHOLD, Duration.ofSeconds(30), e -> true);
        CountBasedCircuitBreaker circuitBreaker = new CountBasedCircuitBreaker(SERVICE, cbConfig, repository);
        RetryConfig retryConfig = new RetryConfig(3, Duration.ZERO, 1.0, Set.of());
        caller = new ResilientCaller(circuitBreaker, new RetryExecutor(), retryConfig);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ścieżka sukcesu
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gdy serwis zewnętrzny odpowiada poprawnie")
    class WhenServiceAvailable {

        @Test
        @DisplayName("zwraca wynik bez żadnych dodatkowych akcji")
        void returnsResult() {
            String result = caller.call(() -> "dane z serwisu");

            assertThat(result).isEqualTo("dane z serwisu");
        }

        @Test
        @DisplayName("próbuje ponownie gdy serwis chwilowo nie odpowiada i wraca do działania")
        void retriesAndReturnsResultOnRecovery() {
            AtomicInteger calls = new AtomicInteger(0);
            // Serwis jest niedostępny przez pierwsze 2 próby, wraca na 3.
            String result = caller.call(() -> {
                if (calls.incrementAndGet() < 3) throw new RuntimeException("chwilowa niedostępność");
                return "dane po recovery";
            });

            assertThat(result).isEqualTo("dane po recovery");
            assertThat(calls.get()).isEqualTo(3);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Zliczanie błędów — kluczowa właściwość CB+Retry
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Zliczanie błędów przez Circuit Breaker")
    class FailureCounting {

        @Test
        @DisplayName("CB liczy 1 failure po wyczerpaniu wszystkich prób retry — nie 1 za każdą próbę")
        void countsOneFailurePerRetrySeriesNotPerAttempt() {
            // Retry robi 3 próby → CB powinien zarejestrować 1 failure (nie 3)
            assertThatThrownBy(() -> caller.call(() -> { throw new RuntimeException("serwis nie działa"); }));

            assertThat(repository.getOrInit(SERVICE).failureCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("CB otwiera się dopiero gdy failure count osiągnie próg (nie wcześniej)")
        void circuitBreakerOpensOnlyAfterThresholdExhausted() {
            // CB_THRESHOLD-1 kompletnych serii retry → CB nadal zamknięty
            for (int i = 0; i < CB_THRESHOLD - 1; i++) {
                int nr = i;
                assertThatThrownBy(() -> caller.call(() -> { throw new RuntimeException("seria " + nr); }));
            }
            assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.CLOSED);

            // Jeszcze jedna seria → CB otwiera się
            assertThatThrownBy(() -> caller.call(() -> { throw new RuntimeException("ostatnia seria"); }));

            assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.OPEN);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Otwarta linia — CB chroni serwis przed kolejnymi wywołaniami
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gdy Circuit Breaker jest otwarty")
    class WhenCircuitBreakerOpen {

        @Test
        @DisplayName("odrzuca wywołanie natychmiast bez wołania serwisu ani retry")
        void rejectsCallWithoutInvokingSupplier() {
            // Otwieramy CB przez CB_THRESHOLD serii failurów
            for (int i = 0; i < CB_THRESHOLD; i++) {
                assertThatThrownBy(() -> caller.call(() -> { throw new RuntimeException(); }));
            }

            AtomicInteger supplierCalls = new AtomicInteger(0);
            assertThatThrownBy(() -> caller.call(() -> {
                supplierCalls.incrementAndGet(); // to nie powinno się wykonać
                return "nie dotrę tu";
            })).isInstanceOf(CircuitOpenException.class);

            // Serwis nie był wołany — CB odrzucił przed wysłaniem żądania
            assertThat(supplierCalls.get()).isZero();
        }
    }
}
