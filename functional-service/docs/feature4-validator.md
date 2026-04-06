# Feature 4: Validator

## Architektura

Trzy klasy w pakiecie `pl.kathelan.functional.feature4`:

| Klasa | Rola |
|---|---|
| `ValidationResult` | Immutable value object (Java record) — wynik walidacji: `boolean valid` + `List<String> errors` |
| `Validator<T>` | Fluent builder — zbiera reguły (`Predicate<T>` + komunikat błędu), wykonuje je i zwraca `ValidationResult` |
| `feature4/model/User.java` | Prosty model danych (Lombok `@Data @Builder`) używany w testach |

### Przepływ danych

```
Validator.<User>of()              // pusty Validator (immutable lista reguł)
    .addRule(predicate, message)  // zwraca NOWY Validator z dodaną regułą
    .addRule(...)
    .validate(user)               // iteruje po regułach, zbiera błędy → ValidationResult
```

`addRule` i `and` zawsze zwracają **nową** instancję — oryginalny walidator nie jest modyfikowany. Dzięki temu walidatory mogą być przechowywane jako stałe i kompozowane bezpiecznie.

### Wewnętrzny record `RuleEntry<T>`

Prywatny rekord łączy `Predicate<T>` z komunikatem błędu. Lista `RuleEntry` jest przechowywana jako `List.copyOf(...)` — niemutowalna.

### Metoda `and(Validator<T> other)`

Łączy reguły obu validatorów: kopiuje `this.rules` + `other.rules` do nowej listy, tworzy nowy `Validator`. Pozwala budować złożone walidatory z mniejszych, reużywalnych komponentów.

### `ValidationResult` jako Java record

`record ValidationResult(boolean valid, List<String> errors)` — kompaktowy, niemutowalny. Customowy konstruktor kanoniczny wywołuje `List.copyOf(errors)`, więc nie można zmodyfikować listy z zewnątrz.

## Wnioski

- **Walidacja nie short-circuituje** — wszystkie reguły są zawsze sprawdzane. Dzięki temu użytkownik dostaje pełną listę błędów naraz, a nie tylko pierwszą usterkę.
- **Predicate vs Exception** — zamiast rzucać wyjątek przy pierwszym błędzie (co jest anty-wzorcem dla walidacji formularzy), zbieramy wszystkie błędy do listy.
- **`List.copyOf` w konstruktorze** — niezbędne, by defensywnie chronić niemutowalność rekordu; zwykłe przypisanie pozostawiłoby lukę jeśli wywołujący zachował referencję do mutowalnej listy.
- **Composability przez `and()`** — walidatory mogą być tworzone jako stałe per-reguła i łączone ad-hoc, co eliminuje duplikację kodu w testach i produkcji.
- **Pusta lista reguł** — walidator bez reguł zawsze zwraca `success()`. Dobry punkt startowy i edge case wart przetestowania.
