package pl.kathelan.common.resilience.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import pl.kathelan.common.resilience.exception.CircuitOpenException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Circuit Breaker (wyłącznik automatyczny) — chroni system przed kaskadowymi awariami.
 *
 * Działa jak bezpiecznik elektryczny: gdy serwis zdalny przestaje odpowiadać,
 * CB "otwiera" się i odrzuca kolejne wywołania zamiast czekać na timeout.
 * Po upływie czasu daje serwisowi szansę wrócić do działania ("probe").
 *
 * Stany:
 *   CLOSED   → normalne działanie, zlicza błędy
 *   OPEN     → odrzuca wszystkie wywołania natychmiast (bez czekania)
 *   HALF_OPEN → przepuszcza jedno wywołanie testowe (probe) żeby sprawdzić czy serwis wrócił
 */
@DisplayName("CountBasedCircuitBreaker")
class CountBasedCircuitBreakerTest {

    private static final String SERVICE = "soap-service";
    // Próg błędów po którym CB się otwiera
    private static final int THRESHOLD = 3;

    private InMemoryCircuitBreakerStateRepository repository;
    private CircuitBreakerConfig config;
    private CountBasedCircuitBreaker cb;

    @BeforeEach
    void setUp() {
        repository = Mockito.spy(new InMemoryCircuitBreakerStateRepository());
        // Konfiguracja: otwórz po 3 błędach, czekaj 10s przed próbą powrotu, każdy wyjątek to błąd
        config = new CircuitBreakerConfig(THRESHOLD, Duration.ofSeconds(10), e -> true);
        cb = new CountBasedCircuitBreaker(SERVICE, config, repository);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stan CLOSED — normalny ruch, CB nie przeszkadza
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Stan CLOSED (zamknięty) — CB przepuszcza wywołania")
    class WhenClosed {

        @Test
        @DisplayName("zwraca wynik wywołania gdy serwis odpowiada poprawnie")
        void returnsResultWhenCallSucceeds() {
            String result = cb.execute(() -> "odpowiedź serwisu");

            assertThat(result).isEqualTo("odpowiedź serwisu");
        }

        @Test
        @DisplayName("nie liczy udanych wywołań jako błędy")
        void doesNotCountSuccessAsFailure() {
            cb.execute(() -> "ok");
            cb.execute(() -> "ok");

            assertThat(repository.getOrInit(SERVICE).failureCount()).isZero();
        }

        @Test
        @DisplayName("nie zapisuje stanu gdy nie ma żadnych błędów (optymalizacja)")
        void doesNotSaveStateOnCleanSuccess() {
            cb.execute(() -> "ok");

            verify(repository, never()).save(Mockito.eq(SERVICE), Mockito.any());
        }

        @Test
        @DisplayName("zlicza wyjątek jako jeden błąd")
        void countsExceptionAsOneFailure() {
            assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("serwis nie odpowiada"); }))
                    .isInstanceOf(RuntimeException.class);

            assertThat(repository.getOrInit(SERVICE).failureCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("resetuje licznik błędów po udanym wywołaniu")
        void resetsFailureCountAfterSuccess() {
            // Dwa błędy pod rząd...
            assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException(); }));
            assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException(); }));

            // ...ale serwis wraca — licznik powinien być wyzerowany
            cb.execute(() -> "wrócił");

            assertThat(repository.getOrInit(SERVICE).failureCount()).isZero();
        }

        @Test
        @DisplayName("nie otwiera się dopóki liczba błędów nie przekroczy progu")
        void staysClosedBeforeThreshold() {
            triggerFailures(THRESHOLD - 1); // jeden mniej niż próg

            assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.CLOSED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Przejście CLOSED → OPEN: próg błędów osiągnięty
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Przejście CLOSED → OPEN — po osiągnięciu progu błędów")
    class WhenThresholdReached {

        @Test
        @DisplayName("otwiera się dokładnie po osiągnięciu progu")
        void opensAfterReachingThreshold() {
            triggerFailures(THRESHOLD);

            assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.OPEN);
        }

        @Test
        @DisplayName("nie zlicza błędów odrzuconych wywołań — tylko realne błędy")
        void doesNotCountRejectedCallsAsFailures() {
            triggerFailures(THRESHOLD); // CB otwiera się
            int failuresAtOpen = repository.getOrInit(SERVICE).failureCount();

            // Wywołanie odrzucone przez OPEN CB — nie powinno zwiększyć licznika
            assertThatThrownBy(() -> cb.execute(() -> "ok")).isInstanceOf(CircuitOpenException.class);

            assertThat(repository.getOrInit(SERVICE).failureCount()).isEqualTo(failuresAtOpen);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stan OPEN — CB "wyłącznik" jest otwarty, odrzuca natychmiast
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Stan OPEN (otwarty) — CB odrzuca wywołania bez wykonania")
    class WhenOpen {

        @Test
        @DisplayName("rzuca CircuitOpenException zamiast wołać serwis")
        void rejectsCallWithCircuitOpenException() {
            triggerFailures(THRESHOLD);

            assertThatThrownBy(() -> cb.execute(() -> "nigdy nie zostanie wywołane"))
                    .isInstanceOf(CircuitOpenException.class)
                    .hasMessageContaining(SERVICE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Przejście OPEN → HALF_OPEN: timeout minął, dajemy serwisowi szansę
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Przejście OPEN → HALF_OPEN — po upływie czasu oczekiwania")
    class WhenTimeoutElapsed {

        @Test
        @DisplayName("zapisuje stan HALF_OPEN w repozytorium zanim puści probe")
        void savesHalfOpenStateBeforeProbe() {
            triggerFailures(THRESHOLD);
            // Symulujemy że minął czas oczekiwania (11s > skonfigurowane 10s)
            forceOpenedAt(Instant.now().minus(Duration.ofSeconds(11)));

            AtomicReference<State> statePodczasProbe = new AtomicReference<>();
            cb.execute(() -> {
                // Sprawdzamy stan podczas wykonywania probe — musi być HALF_OPEN
                statePodczasProbe.set(repository.getOrInit(SERVICE).state());
                return "ok";
            });

            assertThat(statePodczasProbe.get()).isEqualTo(State.HALF_OPEN);
        }

        @Test
        @DisplayName("przepuszcza jedno wywołanie testowe (probe) i zamyka CB gdy serwis odpowiada")
        void allowsProbeCallAndClosesOnSuccess() {
            triggerFailures(THRESHOLD);
            forceOpenedAt(Instant.now().minus(Duration.ofSeconds(11)));

            String result = cb.execute(() -> "serwis wrócił");

            assertThat(result).isEqualTo("serwis wrócił");
            assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.CLOSED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stan HALF_OPEN — przepuszczamy jedno wywołanie testowe
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Stan HALF_OPEN — jedno wywołanie testowe decyduje o losie CB")
    class WhenHalfOpen {

        @Test
        @DisplayName("zamyka CB gdy probe zakończy się sukcesem")
        void closesAfterSuccessfulProbe() {
            forceState(State.HALF_OPEN);

            String result = cb.execute(() -> "serwis działa");

            assertThat(result).isEqualTo("serwis działa");
            assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.CLOSED);
            assertThat(repository.getOrInit(SERVICE).failureCount()).isZero();
        }

        @Test
        @DisplayName("ponownie otwiera CB gdy probe się nie powiedzie")
        void reopensAfterFailedProbe() {
            forceState(State.HALF_OPEN);

            assertThatThrownBy(() -> cb.execute(() -> { throw new RuntimeException("nadal nie działa"); }))
                    .isInstanceOf(RuntimeException.class);

            assertThat(repository.getOrInit(SERVICE).state()).isEqualTo(State.OPEN);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FailurePredicate — nie każdy wyjątek to awaria infrastruktury
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("FailurePredicate — filtrowanie które wyjątki liczą się jako awaria")
    class WithFailurePredicate {

        @Test
        @DisplayName("nie liczy wyjątku jako błąd gdy predykat mówi że to błąd biznesowy")
        void doesNotCountFailureWhenPredicateExcludes() {
            // Konfiguracja: IllegalArgumentException to błąd biznesowy (np. walidacja), nie awaria
            config = new CircuitBreakerConfig(THRESHOLD, Duration.ofSeconds(10),
                    e -> !(e instanceof IllegalArgumentException));
            cb = new CountBasedCircuitBreaker(SERVICE, config, repository);

            assertThatThrownBy(() -> cb.execute(() -> { throw new IllegalArgumentException("nieprawidłowy input"); }));

            // CB nie powinien liczyć tego jako awarii — serwis działa, tylko input był zły
            assertThat(repository.getOrInit(SERVICE).failureCount()).isZero();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void triggerFailures(int count) {
        for (int i = 0; i < count; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("serwis nie odpowiada"); });
            } catch (Exception ignored) {}
        }
    }

    private void forceState(State state) {
        repository.save(SERVICE, new CircuitBreakerState(state, THRESHOLD, Instant.now()));
    }

    private void forceOpenedAt(Instant openedAt) {
        CircuitBreakerState current = repository.getOrInit(SERVICE);
        repository.save(SERVICE, new CircuitBreakerState(current.state(), current.failureCount(), openedAt));
    }
}
