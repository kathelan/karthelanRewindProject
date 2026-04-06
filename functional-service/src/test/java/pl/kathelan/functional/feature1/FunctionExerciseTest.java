package pl.kathelan.functional.feature1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * FEATURE 1: Function<T, R> — interfejs transformujący wartość T w R.
 *
 * Function jest przydatna gdy:
 * - mapujesz jeden typ w drugi (DTO ↔ Entity, CSV → obiekt)
 * - składasz kroki transformacji (trim → lowercase → validate)
 * - chcesz przekazać "jak przetworzyć wartość" jako parametr
 */
class FunctionExerciseTest {

    record Order(String id, String customerName, double amount, String status) {}

    record OrderSummaryDTO(String orderId, double amount, String statusLabel) {}

    record Product(String sku, String name, String category, double price) {}

    record CsvRow(String productSku, String rawPrice, String rawDate) {}

    // --- transform: mapowanie kolekcji ---

    @Test
    @DisplayName("FEATURE 1: Function — transform konwertuje zamówienia na DTO dla odpowiedzi API")
    void transform_shouldMapOrdersToSummaryDTOs() {
        List<Order> orders = List.of(
                new Order("ORD-001", "Jan Kowalski", 150.99, "COMPLETED"),
                new Order("ORD-002", "Anna Nowak", 75.50, "PENDING"),
                new Order("ORD-003", "Piotr Wiśniewski", 320.00, "PROCESSING")
        );

        List<OrderSummaryDTO> dtos = FunctionExercise.transform(orders,
                o -> new OrderSummaryDTO(o.id(), o.amount(), o.status().toLowerCase()));

        assertThat(dtos)
                .extracting(OrderSummaryDTO::orderId, OrderSummaryDTO::amount, OrderSummaryDTO::statusLabel)
                .containsExactly(
                        tuple("ORD-001", 150.99, "completed"),
                        tuple("ORD-002", 75.50, "pending"),
                        tuple("ORD-003", 320.00, "processing")
                );
    }

    @Test
    @DisplayName("FEATURE 1: Function — transform wyciąga emaile z listy użytkowników (bulk mailing)")
    void transform_shouldExtractEmailsFromUsersForBulkMailing() {
        record User(String name, String email, int age) {}

        List<User> users = List.of(
                new User("Anna", "anna@sklep.pl", 28),
                new User("Jan", "jan@sklep.pl", 35),
                new User("Maria", "maria@sklep.pl", 22)
        );

        List<String> emails = FunctionExercise.transform(users, User::email);

        assertThat(emails).containsExactly("anna@sklep.pl", "jan@sklep.pl", "maria@sklep.pl");
    }

    @Test
    @DisplayName("FEATURE 1: Function — transform na pustej liście produktów zwraca pustą listę SKU")
    void transform_shouldReturnEmptyListForEmptyInput() {
        List<String> skus = FunctionExercise.transform(List.<Product>of(), Product::sku);

        assertThat(skus).isEmpty();
    }

    // --- chain: kompozycja funkcji ---

    @Test
    @DisplayName("FEATURE 1: Function — chain normalizuje email od użytkownika (trim → lowercase)")
    void chain_shouldNormalizeUserInputEmail() {
        Function<String, String> normalizeEmail = FunctionExercise.chain(
                String::trim,
                String::toLowerCase
        );

        assertThat(normalizeEmail.apply("  Jan.Kowalski@GMAIL.COM  ")).isEqualTo("jan.kowalski@gmail.com");
        assertThat(normalizeEmail.apply("ANNA@EXAMPLE.COM")).isEqualTo("anna@example.com");
    }

    @Test
    @DisplayName("FEATURE 1: Function — chain przetwarza nazwę produktu na slug URL (trim → lower → replace spaces)")
    void chain_shouldConvertProductNameToUrlSlug() {
        Function<String, String> toSlug = FunctionExercise.chain(
                String::trim,
                s -> s.toLowerCase().replace(" ", "-")
        );

        assertThat(toSlug.apply("  Laptop Gaming Pro  ")).isEqualTo("laptop-gaming-pro");
        assertThat(toSlug.apply("Mysz Bezprzewodowa")).isEqualTo("mysz-bezprzewodowa");
    }

    @Test
    @DisplayName("FEATURE 1: Function — chain wyciąga domenę z emaila (split @ → [1])")
    void chain_shouldExtractDomainFromEmail() {
        Function<String, String> extractDomain = FunctionExercise.chain(
                String::trim,
                email -> email.split("@")[1]
        );

        assertThat(extractDomain.apply("jan@gmail.com")).isEqualTo("gmail.com");
        assertThat(extractDomain.apply("  anna@company.pl  ")).isEqualTo("company.pl");
    }

    // --- tryApply: bezpieczna konwersja ---

    @Test
    @DisplayName("FEATURE 1: Function — tryApply parsuje cenę produktu z CSV (poprawna wartość)")
    void tryApply_shouldParsePriceFromCsvSuccessfully() {
        Optional<Double> price = FunctionExercise.tryApply("149.99", Double::parseDouble);

        assertThat(price).contains(149.99);
    }

    @Test
    @DisplayName("FEATURE 1: Function — tryApply zwraca empty gdy cena w CSV to 'N/A' (brak danych od dostawcy)")
    void tryApply_shouldReturnEmptyForInvalidPriceInCsv() {
        // Dane z zewnętrznego systemu mogą mieć "N/A" zamiast liczby
        Optional<Double> price = FunctionExercise.tryApply("N/A", Double::parseDouble);

        assertThat(price).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 1: Function — tryApply zwraca empty dla null (brak kolumny w imporcie)")
    void tryApply_shouldReturnEmptyForNullInput() {
        Optional<LocalDate> date = FunctionExercise.tryApply(null, LocalDate::parse);

        assertThat(date).isEmpty();
    }

    // --- toMap: budowanie indeksów ---

    @Test
    @DisplayName("FEATURE 1: Function — toMap buduje indeks produktów po SKU dla szybkiego lookup przy zamówieniu")
    void toMap_shouldBuildProductIndexBySku() {
        List<Product> products = List.of(
                new Product("SKU-001", "Laptop", "Elektronika", 2_999.99),
                new Product("SKU-002", "Mysz", "Akcesoria", 49.99),
                new Product("SKU-003", "Monitor", "Elektronika", 799.99)
        );

        Map<String, Product> index = FunctionExercise.toMap(products, Product::sku);

        assertThat(index.get("SKU-002").name()).isEqualTo("Mysz");
        assertThat(index.get("SKU-001").price()).isEqualTo(2_999.99);
        assertThat(index).hasSize(3);
    }

    @Test
    @DisplayName("FEATURE 1: Function — toMap buduje mapę ID → Order dla szybkiego wyszukiwania statusu")
    void toMap_shouldIndexOrdersById() {
        List<Order> orders = List.of(
                new Order("ORD-100", "Jan", 100.0, "PENDING"),
                new Order("ORD-200", "Anna", 200.0, "COMPLETED"),
                new Order("ORD-300", "Piotr", 300.0, "CANCELLED")
        );

        Map<String, Order> orderIndex = FunctionExercise.toMap(orders, Order::id);

        assertThat(orderIndex.get("ORD-200").status()).isEqualTo("COMPLETED");
        assertThat(orderIndex.get("ORD-100").customerName()).isEqualTo("Jan");
    }

    @Test
    @DisplayName("FEATURE 1: Function — toMap rzuca wyjątek gdy dwa produkty mają ten sam SKU (błąd importu)")
    void toMap_shouldThrowWhenDuplicateSkuDetectedDuringImport() {
        List<Product> duplicates = List.of(
                new Product("SKU-DUPLICATE", "Produkt A", "Kat", 10.0),
                new Product("SKU-DUPLICATE", "Produkt B", "Kat", 20.0)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> FunctionExercise.toMap(duplicates, Product::sku))
                .isInstanceOf(IllegalStateException.class);
    }
}
