package pl.kathelan.functional.feature1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FEATURE 1: Predicate<T> — interfejs testujący warunek, zwraca boolean.
 *
 * Predicate jest przydatny gdy:
 * - filtrujesz kolekcje według reguł biznesowych
 * - budujesz dynamiczne warunki (np. walidacja, reguły rabatowe)
 * - chcesz składać warunki (and/or/negate) zamiast pisać złożone if-y
 */
class PredicateExerciseTest {

    record User(String name, String email, int age, boolean premium) {}

    record Order(String id, String status, double amount, String currency) {}

    record LoanApplicant(String name, int age, double monthlyIncome, int creditScore) {}

    record Transaction(String id, double amount, boolean foreignCountry, boolean newCustomer) {}

    // --- filter ---

    @Test
    @DisplayName("FEATURE 1: Predicate — filter zwraca tylko pełnoletnich użytkowników (weryfikacja wieku)")
    void filter_shouldReturnOnlyAdultUsers() {
        List<User> users = List.of(
                new User("Anna", "anna@test.com", 16, false),
                new User("Jan", "jan@test.com", 25, true),
                new User("Tomek", "tomek@test.com", 30, false),
                new User("Kasia", "kasia@test.com", 17, false)
        );

        List<User> adults = PredicateExercise.filter(users, u -> u.age() >= 18);

        assertThat(adults)
                .hasSize(2)
                .extracting(User::name)
                .containsExactly("Jan", "Tomek");
    }

    @Test
    @DisplayName("FEATURE 1: Predicate — filter zwraca premium userów do wysyłki newslettera VIP")
    void filter_shouldReturnPremiumUsersForVipNewsletter() {
        List<User> users = List.of(
                new User("Anna", "anna@test.com", 25, true),
                new User("Jan", "jan@test.com", 30, false),
                new User("Maria", "maria@test.com", 28, true),
                new User("Piotr", "piotr@test.com", 35, false)
        );

        List<User> vipRecipients = PredicateExercise.filter(users, User::premium);

        assertThat(vipRecipients)
                .extracting(User::email)
                .containsExactly("anna@test.com", "maria@test.com");
    }

    @Test
    @DisplayName("FEATURE 1: Predicate — filter na pustej bazie użytkowników zwraca pustą listę")
    void filter_shouldReturnEmptyListWhenNoUsers() {
        List<User> result = PredicateExercise.filter(List.of(), u -> u.premium());

        assertThat(result).isEmpty();
    }

    // --- count ---

    @Test
    @DisplayName("FEATURE 1: Predicate — count zlicza zamówienia oczekujące na realizację")
    void count_shouldCountPendingOrders() {
        List<Order> orders = List.of(
                new Order("ORD-001", "PENDING", 150.0, "PLN"),
                new Order("ORD-002", "COMPLETED", 200.0, "PLN"),
                new Order("ORD-003", "PENDING", 75.0, "PLN"),
                new Order("ORD-004", "CANCELLED", 300.0, "PLN"),
                new Order("ORD-005", "PENDING", 50.0, "PLN")
        );

        long pendingCount = PredicateExercise.count(orders, o -> "PENDING".equals(o.status()));

        assertThat(pendingCount).isEqualTo(3);
    }

    @Test
    @DisplayName("FEATURE 1: Predicate — count zlicza zamówienia powyżej limitu wymagające manualnej weryfikacji")
    void count_shouldCountHighValueOrdersRequiringManualReview() {
        List<Order> orders = List.of(
                new Order("ORD-001", "PENDING", 500.0, "PLN"),
                new Order("ORD-002", "PENDING", 15_000.0, "PLN"),
                new Order("ORD-003", "PENDING", 8_000.0, "PLN"),
                new Order("ORD-004", "PENDING", 25_000.0, "PLN")
        );

        long highValueCount = PredicateExercise.count(orders, o -> o.amount() > 10_000);

        assertThat(highValueCount).isEqualTo(2);
    }

    @Test
    @DisplayName("FEATURE 1: Predicate — count gdy żadne zamówienie nie spełnia warunku zwraca 0")
    void count_shouldReturnZeroWhenNoPendingOrders() {
        List<Order> orders = List.of(
                new Order("ORD-001", "COMPLETED", 100.0, "PLN"),
                new Order("ORD-002", "COMPLETED", 200.0, "PLN")
        );

        long pendingCount = PredicateExercise.count(orders, o -> "PENDING".equals(o.status()));

        assertThat(pendingCount).isZero();
    }

    // --- allOf: składanie warunków przez AND ---

    @Test
    @DisplayName("FEATURE 1: Predicate — allOf sprawdza czy klient kwalifikuje się na kredyt (wiek + dochód + historia)")
    void allOf_shouldCheckLoanEligibilityUsingMultipleCriteria() {
        Predicate<LoanApplicant> eligible = PredicateExercise.allOf(List.of(
                a -> a.age() >= 18,
                a -> a.monthlyIncome() >= 3_000,
                a -> a.creditScore() >= 600
        ));

        LoanApplicant qualifies = new LoanApplicant("Jan Kowalski", 25, 4_500, 720);
        LoanApplicant tooYoung = new LoanApplicant("Kacper Nowak", 17, 5_000, 750);
        LoanApplicant lowIncome = new LoanApplicant("Anna Wiśniewska", 30, 2_000, 700);
        LoanApplicant badCredit = new LoanApplicant("Piotr Zając", 28, 4_000, 450);

        assertThat(eligible.test(qualifies)).isTrue();
        assertThat(eligible.test(tooYoung)).isFalse();
        assertThat(eligible.test(lowIncome)).isFalse();
        assertThat(eligible.test(badCredit)).isFalse();
    }

    @Test
    @DisplayName("FEATURE 1: Predicate — allOf z pustą listą reguł zawsze przepuszcza (brak ograniczeń)")
    void allOf_shouldAlwaysPassWhenNoRulesConfigured() {
        Predicate<LoanApplicant> noRules = PredicateExercise.allOf(List.of());

        LoanApplicant anyone = new LoanApplicant("Ktokolwiek", 10, 0, 0);
        assertThat(noRules.test(anyone)).isTrue();
    }

    // --- anyOf: składanie warunków przez OR ---

    @Test
    @DisplayName("FEATURE 1: Predicate — anyOf oznacza transakcję jako podejrzaną (duża kwota LUB zagranica LUB nowy klient)")
    void anyOf_shouldFlagSuspiciousTransactionWhenAnyRuleMatches() {
        Predicate<Transaction> suspicious = PredicateExercise.anyOf(List.of(
                t -> t.amount() > 10_000,
                t -> t.foreignCountry(),
                t -> t.newCustomer()
        ));

        Transaction normal = new Transaction("TXN-001", 500, false, false);
        Transaction bigAmount = new Transaction("TXN-002", 15_000, false, false);
        Transaction foreign = new Transaction("TXN-003", 100, true, false);
        Transaction newCustomer = new Transaction("TXN-004", 200, false, true);
        Transaction allFlags = new Transaction("TXN-005", 50_000, true, true);

        assertThat(suspicious.test(normal)).isFalse();     // nic nie wzbudza podejrzeń
        assertThat(suspicious.test(bigAmount)).isTrue();    // duża kwota
        assertThat(suspicious.test(foreign)).isTrue();      // zagranica
        assertThat(suspicious.test(newCustomer)).isTrue();  // nowy klient
        assertThat(suspicious.test(allFlags)).isTrue();     // wszystko
    }

    @Test
    @DisplayName("FEATURE 1: Predicate — anyOf z pustą listą reguł nigdy nie przepuszcza")
    void anyOf_shouldNeverPassWhenNoRulesConfigured() {
        Predicate<Transaction> noRules = PredicateExercise.anyOf(List.of());

        Transaction any = new Transaction("TXN-999", 9999, true, true);
        assertThat(noRules.test(any)).isFalse();
    }

    // --- partition: podział na dwie grupy ---

    @Test
    @DisplayName("FEATURE 1: Predicate — partition dzieli zamówienia na zrealizowane i w toku (dashboard)")
    void partition_shouldSeparateCompletedFromActiveOrders() {
        List<Order> orders = List.of(
                new Order("ORD-001", "COMPLETED", 100.0, "PLN"),
                new Order("ORD-002", "PENDING", 200.0, "PLN"),
                new Order("ORD-003", "COMPLETED", 300.0, "PLN"),
                new Order("ORD-004", "PROCESSING", 150.0, "PLN")
        );

        Map<Boolean, List<Order>> result = PredicateExercise.partition(
                orders, o -> "COMPLETED".equals(o.status()));

        assertThat(result.get(true))
                .hasSize(2)
                .extracting(Order::id)
                .containsExactly("ORD-001", "ORD-003");

        assertThat(result.get(false))
                .hasSize(2)
                .extracting(Order::id)
                .containsExactly("ORD-002", "ORD-004");
    }

    @Test
    @DisplayName("FEATURE 1: Predicate — partition walut: krajowe PLN vs zagraniczne (raport finansowy)")
    void partition_shouldSeparateDomesticFromForeignCurrencyOrders() {
        List<Order> orders = List.of(
                new Order("ORD-001", "COMPLETED", 500.0, "PLN"),
                new Order("ORD-002", "COMPLETED", 200.0, "EUR"),
                new Order("ORD-003", "COMPLETED", 750.0, "PLN"),
                new Order("ORD-004", "COMPLETED", 100.0, "USD")
        );

        Map<Boolean, List<Order>> result = PredicateExercise.partition(
                orders, o -> "PLN".equals(o.currency()));

        assertThat(result.get(true)).hasSize(2);  // PLN
        assertThat(result.get(false)).hasSize(2); // EUR + USD
    }

    @Test
    @DisplayName("FEATURE 1: Predicate — partition na pustej liście zwraca dwie puste grupy")
    void partition_shouldReturnTwoEmptyGroupsForEmptyInput() {
        Map<Boolean, List<Order>> result = PredicateExercise.partition(List.of(), o -> true);

        assertThat(result.get(true)).isEmpty();
        assertThat(result.get(false)).isEmpty();
    }
}
