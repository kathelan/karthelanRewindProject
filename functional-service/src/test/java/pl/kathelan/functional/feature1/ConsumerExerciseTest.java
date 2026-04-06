package pl.kathelan.functional.feature1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FEATURE 1: Consumer<T> — interfejs wykonujący operację na wartości, nic nie zwraca.
 *
 * Consumer jest przydatny gdy:
 * - wykonujesz side effecty (zapis do bazy, wysyłka maila, logowanie)
 * - budujesz łańcuch operacji przez andThen (log + audit + metrics)
 * - przekazujesz callback do wywołania dla każdego elementu
 */
class ConsumerExerciseTest {

    record User(String name, String email, boolean premium) {}

    record Order(String id, double amount, String status) {}

    // --- applyToAll: wykonaj operację na każdym elemencie ---

    @Test
    @DisplayName("FEATURE 1: Consumer — applyToAll wysyła powiadomienie email do każdego użytkownika")
    void applyToAll_shouldSendEmailNotificationToEachUser() {
        List<String> sentEmails = new ArrayList<>();
        List<User> users = List.of(
                new User("Anna", "anna@test.com", true),
                new User("Jan", "jan@test.com", false),
                new User("Maria", "maria@test.com", true)
        );

        ConsumerExercise.applyToAll(users, u -> sentEmails.add(u.email()));

        assertThat(sentEmails).containsExactly("anna@test.com", "jan@test.com", "maria@test.com");
    }

    @Test
    @DisplayName("FEATURE 1: Consumer — applyToAll nie wysyła maili gdy lista odbiorców jest pusta")
    void applyToAll_shouldNotSendEmailsWhenNoRecipients() {
        List<String> sentEmails = new ArrayList<>();

        ConsumerExercise.applyToAll(List.of(), (User u) -> sentEmails.add(u.email()));

        assertThat(sentEmails).isEmpty();
    }

    @Test
    @DisplayName("FEATURE 1: Consumer — applyToAll aplikuje rabat do każdego zamówienia w koszyku")
    void applyToAll_shouldApplyDiscountToAllOrdersInCart() {
        List<String> processedOrders = new ArrayList<>();
        List<Order> cart = List.of(
                new Order("ORD-001", 100.0, "PENDING"),
                new Order("ORD-002", 200.0, "PENDING"),
                new Order("ORD-003", 50.0, "PENDING")
        );

        // Symulacja: oznacz zamówienie jako "rabat zastosowany"
        ConsumerExercise.applyToAll(cart, o -> processedOrders.add(o.id() + ":DISCOUNTED"));

        assertThat(processedOrders).containsExactly("ORD-001:DISCOUNTED", "ORD-002:DISCOUNTED", "ORD-003:DISCOUNTED");
    }

    // --- combine: łańcuch operacji (log + audit) ---

    @Test
    @DisplayName("FEATURE 1: Consumer — combine wykonuje log aplikacji a potem zapis do audit logu (oba w kolejności)")
    void combine_shouldRunApplicationLogThenAuditLogInOrder() {
        List<String> appLog = new ArrayList<>();
        List<String> auditLog = new ArrayList<>();

        Consumer<Order> pipeline = ConsumerExercise.combine(
                o -> appLog.add("[INFO] Order processed: " + o.id()),
                o -> auditLog.add("[AUDIT] " + o.id() + " by system")
        );

        pipeline.accept(new Order("ORD-001", 150.0, "COMPLETED"));

        assertThat(appLog).containsExactly("[INFO] Order processed: ORD-001");
        assertThat(auditLog).containsExactly("[AUDIT] ORD-001 by system");
    }

    @Test
    @DisplayName("FEATURE 1: Consumer — combine kolejność ma znaczenie: najpierw zapis do DB, potem wysyłka maila")
    void combine_shouldPreserveOrderDbSaveBeforeEmailSend() {
        List<String> executionLog = new ArrayList<>();

        Consumer<User> onRegister = ConsumerExercise.combine(
                u -> executionLog.add("1. SAVE_TO_DB: " + u.name()),
                u -> executionLog.add("2. SEND_WELCOME_EMAIL: " + u.email())
        );

        onRegister.accept(new User("Anna", "anna@test.com", false));

        assertThat(executionLog).containsExactly(
                "1. SAVE_TO_DB: Anna",
                "2. SEND_WELCOME_EMAIL: anna@test.com"
        );
    }

    @Test
    @DisplayName("FEATURE 1: Consumer — combine działa na wielu elementach (batch processing)")
    void combine_shouldWorkForBatchProcessing() {
        List<String> savedIds = new ArrayList<>();
        List<String> indexedIds = new ArrayList<>();

        Consumer<Order> batchProcessor = ConsumerExercise.combine(
                o -> savedIds.add(o.id()),
                o -> indexedIds.add(o.id())
        );

        List.of(
                new Order("ORD-001", 100.0, "COMPLETED"),
                new Order("ORD-002", 200.0, "COMPLETED")
        ).forEach(batchProcessor);

        assertThat(savedIds).containsExactly("ORD-001", "ORD-002");
        assertThat(indexedIds).containsExactly("ORD-001", "ORD-002");
    }

    // --- conditional: wykonaj operację tylko gdy warunek spełniony ---

    @Test
    @DisplayName("FEATURE 1: Consumer — conditional wysyła SMS tylko do premium użytkowników")
    void conditional_shouldSendSmsOnlyToPremiumUsers() {
        List<String> smsRecipients = new ArrayList<>();

        Consumer<User> sendPremiumSms = ConsumerExercise.conditional(
                User::premium,
                u -> smsRecipients.add(u.name())
        );

        List.of(
                new User("Anna", "anna@test.com", true),   // dostanie SMS
                new User("Jan", "jan@test.com", false),    // NIE dostanie
                new User("Maria", "maria@test.com", true), // dostanie SMS
                new User("Piotr", "piotr@test.com", false) // NIE dostanie
        ).forEach(sendPremiumSms);

        assertThat(smsRecipients).containsExactly("Anna", "Maria");
    }

    @Test
    @DisplayName("FEATURE 1: Consumer — conditional wysyła alert tylko dla zamówień powyżej limitu")
    void conditional_shouldAlertOnlyForHighValueOrders() {
        List<String> alerts = new ArrayList<>();

        Consumer<Order> alertHighValue = ConsumerExercise.conditional(
                o -> o.amount() > 5_000,
                o -> alerts.add("HIGH_VALUE_ALERT: " + o.id())
        );

        List.of(
                new Order("ORD-001", 500.0, "PENDING"),    // bez alertu
                new Order("ORD-002", 15_000.0, "PENDING"), // alert!
                new Order("ORD-003", 200.0, "PENDING"),    // bez alertu
                new Order("ORD-004", 8_000.0, "PENDING")   // alert!
        ).forEach(alertHighValue);

        assertThat(alerts).containsExactly("HIGH_VALUE_ALERT: ORD-002", "HIGH_VALUE_ALERT: ORD-004");
    }

    // --- processMap: iterowanie po mapie przez BiConsumer ---

    @Test
    @DisplayName("FEATURE 1: Consumer — processMap drukuje pozycje faktury (nazwa → cena)")
    void processMap_shouldPrintInvoiceLineItems() {
        Map<String, Double> invoiceItems = new LinkedHashMap<>();
        invoiceItems.put("Laptop Dell XPS", 3_499.99);
        invoiceItems.put("Mysz Logitech MX", 149.99);
        invoiceItems.put("Podkładka", 29.99);

        List<String> invoiceLines = new ArrayList<>();
        ConsumerExercise.processMap(invoiceItems, (name, price) ->
                invoiceLines.add(name + ": " + price + " PLN"));

        assertThat(invoiceLines).containsExactly(
                "Laptop Dell XPS: 3499.99 PLN",
                "Mysz Logitech MX: 149.99 PLN",
                "Podkładka: 29.99 PLN"
        );
    }

    @Test
    @DisplayName("FEATURE 1: Consumer — processMap loguje uprawnienia użytkownika (rola → akcje)")
    void processMap_shouldLogUserPermissions() {
        Map<String, List<String>> rolePermissions = new LinkedHashMap<>();
        rolePermissions.put("ADMIN", List.of("READ", "WRITE", "DELETE"));
        rolePermissions.put("USER", List.of("READ"));

        List<String> permissionLog = new ArrayList<>();
        ConsumerExercise.processMap(rolePermissions, (role, perms) ->
                permissionLog.add(role + " -> " + perms.size() + " permissions"));

        assertThat(permissionLog).containsExactly(
                "ADMIN -> 3 permissions",
                "USER -> 1 permissions"
        );
    }

    @Test
    @DisplayName("FEATURE 1: Consumer — processMap na pustym koszyku nic nie robi")
    void processMap_shouldDoNothingForEmptyCart() {
        List<String> lines = new ArrayList<>();

        ConsumerExercise.processMap(Map.of(), (String product, Double price) -> lines.add(product));

        assertThat(lines).isEmpty();
    }
}
