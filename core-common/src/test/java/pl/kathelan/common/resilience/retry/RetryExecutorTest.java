package pl.kathelan.common.resilience.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry — automatyczne ponawianie wywołań gdy serwis chwilowo nie odpowiada.
 *
 * Zamiast natychmiast zwracać błąd do użytkownika, RetryExecutor próbuje wywołać
 * serwis kilka razy z rosnącymi przerwami (exponential backoff).
 *
 * Można skonfigurować:
 *   - maxAttempts  — ile razy łącznie próbować (wliczając pierwsze wywołanie)
 *   - initialDelay — pauza przed drugą próbą
 *   - multiplier   — o ile rośnie pauza z każdą kolejną próbą (np. 2.0 = podwaja)
 *   - retryOn      — tylko te wyjątki powodują retry (puste = wszystkie RuntimeException)
 *   - excludeOn    — te wyjątki NIGDY nie powodują retry (np. błędy walidacji)
 */
@DisplayName("RetryExecutor")
class RetryExecutorTest {

    private final RetryExecutor executor = new RetryExecutor();

    /**
     * Wersja testowa RetryExecutora — rejestruje wywołania sleep zamiast naprawdę czekać.
     * Pozwala sprawdzić kiedy i z jakimi wartościami sleep był wołany, bez spowalniania testów.
     */
    private static class TrackingRetryExecutor extends RetryExecutor {
        final List<Long> sleepDurations = new ArrayList<>();

        @Override
        protected void sleep(long ms) {
            sleepDurations.add(ms);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ścieżka sukcesu
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gdy wywołanie kończy się sukcesem")
    class WhenCallSucceeds {

        @Test
        @DisplayName("zwraca wynik bez ponowień gdy pierwsze wywołanie działa")
        void returnsResultOnFirstSuccess() {
            String result = executor.execute(() -> "odpowiedź", RetryConfig.defaults());

            assertThat(result).isEqualTo("odpowiedź");
        }

        @Test
        @DisplayName("zwraca wynik gdy serwis wraca do działania w trakcie retry")
        void returnsResultAfterTransientFailures() {
            AtomicInteger attempts = new AtomicInteger(0);
            // Serwis odpowiada dopiero na 3. próbie
            RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0, Set.of());

            String result = executor.execute(() -> {
                if (attempts.incrementAndGet() < 3) throw new RuntimeException("jeszcze nie");
                return "działa";
            }, config);

            assertThat(result).isEqualTo("działa");
            assertThat(attempts.get()).isEqualTo(3);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wyczerpanie prób
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Gdy wszystkie próby się wyczerpią")
    class WhenAllAttemptsExhausted {

        @Test
        @DisplayName("rzuca ostatni wyjątek jaki dostał od serwisu")
        void rethrowsLastException() {
            RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0, Set.of());

            assertThatThrownBy(() -> executor.execute(() -> {
                throw new RuntimeException("serwis ciągle nie odpowiada");
            }, config))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("serwis ciągle nie odpowiada");
        }

        @Test
        @DisplayName("próbuje dokładnie tyle razy ile skonfigurowano w maxAttempts")
        void attemptsExactlyMaxAttemptsTimes() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryConfig config = new RetryConfig(5, Duration.ZERO, 1.0, Set.of());

            assertThatThrownBy(() -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException();
            }, config));

            assertThat(attempts.get()).isEqualTo(5);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Filtrowanie wyjątków — nie każdy błąd zasługuje na retry
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Filtrowanie wyjątków — kiedy retry ma sens a kiedy nie")
    class ExceptionFiltering {

        @Test
        @DisplayName("nie ponawia gdy wyjątek nie pasuje do listy retryOn")
        void doesNotRetryWhenExceptionNotInRetryOn() {
            AtomicInteger attempts = new AtomicInteger(0);
            // Retry tylko dla IllegalStateException — RuntimeException go nie wyzwoli
            RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0,
                    Set.of(IllegalStateException.class));

            assertThatThrownBy(() -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("nie ten typ wyjątku");
            }, config));

            // Tylko 1 próba — brak retry
            assertThat(attempts.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("ponawia gdy wyjątek pasuje do listy retryOn")
        void retriesWhenExceptionMatchesRetryOn() {
            AtomicInteger attempts = new AtomicInteger(0);
            RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0,
                    Set.of(RuntimeException.class));

            assertThatThrownBy(() -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("ten typ powoduje retry");
            }, config));

            assertThat(attempts.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("nie ponawia gdy wyjątek jest na liście excludeOn — nawet jeśli pasuje do retryOn")
        void doesNotRetryWhenExceptionIsExcluded() {
            AtomicInteger attempts = new AtomicInteger(0);
            // retryOn: wszystkie RuntimeException, ale excludeOn: IllegalArgumentException ma priorytet
            RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0,
                    Set.of(RuntimeException.class),
                    Set.of(IllegalArgumentException.class));

            assertThatThrownBy(() -> executor.execute(() -> {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("błąd walidacji — nie retry");
            }, config)).isInstanceOf(IllegalArgumentException.class);

            // excludeOn ma priorytet — tylko 1 próba, brak retry
            assertThat(attempts.get()).isEqualTo(1);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exponential backoff — pauzy między próbami
    // Tracking executor: sprawdza KIEDY i Z JAKIMI wartościami sleep jest wołany
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Exponential backoff — rosnące pauzy między próbami")
    class ExponentialBackoff {

        @Test
        @DisplayName("podwaja czas oczekiwania po każdej nieudanej próbie")
        void doublesDelayAfterEachFailure() {
            TrackingRetryExecutor tracking = new TrackingRetryExecutor();
            // 3 próby, start od 50ms, multiplier 2.0 → pauzy: 50ms, 100ms
            RetryConfig config = new RetryConfig(3, Duration.ofMillis(50), 2.0, Set.of());

            assertThatThrownBy(() -> tracking.execute(() -> { throw new RuntimeException(); }, config));

            assertThat(tracking.sleepDurations).containsExactly(50L, 100L);
        }

        @Test
        @DisplayName("czeka tylko między próbami — NIE czeka po ostatniej nieudanej")
        void sleepsOnlyBetweenAttempts_notAfterLast() {
            TrackingRetryExecutor tracking = new TrackingRetryExecutor();
            // 4 próby → 3 pauzy (między 1-2, 2-3, 3-4) — nie po 4.
            RetryConfig config = new RetryConfig(4, Duration.ofMillis(10), 1.0, Set.of());

            assertThatThrownBy(() -> tracking.execute(() -> { throw new RuntimeException(); }, config));

            assertThat(tracking.sleepDurations).hasSize(3);
        }

        @Test
        @DisplayName("przekazuje delay=0 do sleep gdy initialDelay wynosi zero")
        void passesDurationZeroToSleep() {
            TrackingRetryExecutor tracking = new TrackingRetryExecutor();
            RetryConfig config = new RetryConfig(3, Duration.ZERO, 1.0, Set.of());

            assertThatThrownBy(() -> tracking.execute(() -> { throw new RuntimeException(); }, config));

            // Sleep wołany 2 razy (między próbami), ale z wartością 0
            assertThat(tracking.sleepDurations).hasSize(2).containsOnly(0L);
        }

        @Test
        @Tag("slow")
        @DisplayName("naprawdę blokuje wątek podczas oczekiwania (nie tylko rejestruje)")
        void actuallyBlocksThreadDuringSleep() {
            // Prawdziwy executor (nie tracking) — weryfikuje że Thread.sleep jest wywoływany.
            // @Tag("slow") bo mierzy realny czas — może być niestabilny pod obciążeniem CI.
            RetryConfig config = new RetryConfig(3, Duration.ofMillis(50), 2.0, Set.of());

            long start = System.currentTimeMillis();
            assertThatThrownBy(() -> executor.execute(() -> { throw new RuntimeException(); }, config));
            long elapsed = System.currentTimeMillis() - start;

            // 50ms + 100ms = minimum 150ms realnego czasu
            assertThat(elapsed).isGreaterThanOrEqualTo(150);
        }
    }
}
