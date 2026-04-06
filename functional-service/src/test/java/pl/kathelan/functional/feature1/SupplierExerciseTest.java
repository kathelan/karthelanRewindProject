package pl.kathelan.functional.feature1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FEATURE 1: Supplier<T> — interfejs dostarczający wartość bez argumentów.
 *
 * Supplier jest przydatny gdy:
 * - chcesz opóźnić obliczenie do momentu gdy faktycznie potrzebne (lazy evaluation)
 * - chcesz przekazać "jak uzyskać wartość" zamiast samej wartości
 * - potrzebujesz fabryki generującej obiekty
 */
class SupplierExerciseTest {

    record AppConfig(String dbUrl, int maxConnections, String apiKey) {}

    // --- lazy: opóźnione ładowanie konfiguracji ---

    @Test
    @DisplayName("FEATURE 1: Supplier — lazy ładuje konfigurację bazy tylko raz, nie przy każdym zapytaniu")
    void lazy_shouldLoadDatabaseConfigOnlyOnce() {
        AtomicInteger dbCallCount = new AtomicInteger(0);

        Supplier<AppConfig> configLoader = SupplierExercise.lazy(() -> {
            dbCallCount.incrementAndGet(); // symuluje kosztowne zapytanie do bazy / pliku konfiguracyjnego
            return new AppConfig("jdbc:postgresql://prod-db:5432/shop", 10, "secret-api-key");
        });

        // Pierwsze użycie — ładuje config
        AppConfig first = configLoader.get();
        // Kolejne wywołania — powinny zwracać cache, nie iść do bazy
        AppConfig second = configLoader.get();
        AppConfig third = configLoader.get();

        assertThat(first.dbUrl()).isEqualTo("jdbc:postgresql://prod-db:5432/shop");
        assertThat(first).isSameAs(second).isSameAs(third); // ten sam obiekt z cache
        assertThat(dbCallCount.get()).isEqualTo(1); // tylko jedno zapytanie — nie trzy!
    }

    @Test
    @DisplayName("FEATURE 1: Supplier — lazy cache'uje null (np. user bez przypisanego managera)")
    void lazy_shouldCacheNullValue() {
        AtomicInteger dbCallCount = new AtomicInteger(0);

        // Scenariusz: user może nie mieć managera — null to prawidłowa wartość biznesowa
        Supplier<String> managerLoader = SupplierExercise.lazy(() -> {
            dbCallCount.incrementAndGet();
            return null; // brak managera w bazie
        });

        assertThat(managerLoader.get()).isNull();
        assertThat(managerLoader.get()).isNull();
        assertThat(dbCallCount.get()).isEqualTo(1); // null jest cache'owany — nie odpytuje bazy drugi raz
    }

    @Test
    @DisplayName("FEATURE 1: Supplier — lazy konfiguracja jest dostępna dopiero gdy pierwszy moduł jej potrzebuje")
    void lazy_shouldNotInitializeConfigUntilFirstAccess() {
        AtomicInteger initCount = new AtomicInteger(0);

        // Przy tworzeniu Suppliera — ZERO inicjalizacji
        Supplier<AppConfig> config = SupplierExercise.lazy(() -> {
            initCount.incrementAndGet();
            return new AppConfig("jdbc:h2:mem:test", 5, "test-key");
        });

        assertThat(initCount.get()).isZero(); // jeszcze nie załadowano

        config.get(); // dopiero tutaj ładuje
        assertThat(initCount.get()).isEqualTo(1);
    }

    // --- getOrDefault: fallback wartości ---

    @Test
    @DisplayName("FEATURE 1: Supplier — getOrDefault zwraca domyślne zdjęcie gdy użytkownik nie ustawił avatara")
    void getOrDefault_shouldReturnDefaultAvatarWhenUserHasNone() {
        String userAvatar = null; // user nie wgrał własnego zdjęcia
        Supplier<String> defaultAvatar = () -> "https://cdn.example.com/avatars/default.png";

        String result = SupplierExercise.getOrDefault(userAvatar, defaultAvatar);

        assertThat(result).isEqualTo("https://cdn.example.com/avatars/default.png");
    }

    @Test
    @DisplayName("FEATURE 1: Supplier — getOrDefault nie generuje domyślnego adresu gdy user ma własny avatar")
    void getOrDefault_shouldNotInvokeSupplierWhenAvatarExists() {
        AtomicInteger s3CallCount = new AtomicInteger(0);
        String userAvatar = "https://s3.example.com/users/123/avatar.jpg";

        // Gdyby supplier był wywołany — oznaczałoby niepotrzebny call do S3
        String result = SupplierExercise.getOrDefault(userAvatar, () -> {
            s3CallCount.incrementAndGet();
            return "https://cdn.example.com/avatars/default.png";
        });

        assertThat(result).isEqualTo("https://s3.example.com/users/123/avatar.jpg");
        assertThat(s3CallCount.get()).isZero(); // zero niepotrzebnych wywołań S3
    }

    @Test
    @DisplayName("FEATURE 1: Supplier — getOrDefault zwraca adres dostawy z poprzedniego zamówienia gdy brak nowego")
    void getOrDefault_shouldFallbackToPreviousShippingAddress() {
        String currentAddress = null; // user nie podał adresu w nowym zamówieniu
        String lastOrderAddress = "ul. Kwiatowa 5, 00-001 Warszawa";

        String result = SupplierExercise.getOrDefault(currentAddress, () -> lastOrderAddress);

        assertThat(result).isEqualTo("ul. Kwiatowa 5, 00-001 Warszawa");
    }

    // --- generate: fabryka obiektów ---

    @Test
    @DisplayName("FEATURE 1: Supplier — generate tworzy N zamówień testowych z kolejnymi numerami")
    void generate_shouldCreateTestOrdersWithSequentialIds() {
        AtomicInteger sequence = new AtomicInteger(1000);

        List<String> orderIds = SupplierExercise.generate(3, () -> "ORD-" + sequence.getAndIncrement());

        assertThat(orderIds).containsExactly("ORD-1000", "ORD-1001", "ORD-1002");
    }

    @Test
    @DisplayName("FEATURE 1: Supplier — generate tworzy N tokenów sesji (każdy inny — Supplier wywołany N razy)")
    void generate_shouldCreateUniqueSessionTokensForEachUser() {
        List<String> tokens = SupplierExercise.generate(5, () -> UUID.randomUUID().toString());

        assertThat(tokens).hasSize(5);
        assertThat(tokens).doesNotHaveDuplicates(); // każdy token unikalny
    }

    @Test
    @DisplayName("FEATURE 1: Supplier — generate z count=0 zwraca pustą listę (brak użytkowników do powiadomienia)")
    void generate_shouldReturnEmptyListWhenNoUsersToNotify() {
        List<String> notifications = SupplierExercise.generate(0, () -> "Twoje zamówienie jest gotowe");

        assertThat(notifications).isEmpty();
    }
}
