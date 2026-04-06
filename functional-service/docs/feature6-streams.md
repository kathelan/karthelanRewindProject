# Feature 6: Stream API i Optional

## Architektura

Cztery klasy w pakiecie `pl.kathelan.functional.feature6`:

| Klasa | Rola |
|---|---|
| `StreamExercise` | Statyczne metody demonstrujące kluczowe operacje Stream API |
| `OptionalExercise` | Statyczne metody demonstrujące bezpieczną pracę z `Optional` |
| `feature6/model/User.java` | Model użytkownika (Lombok `@Data @Builder`) |
| `feature6/model/Product.java` | Model produktu (Lombok `@Data @Builder`) — używany w testach Stream |

### StreamExercise — przegląd metod

| Metoda | Kolektor / operacja |
|---|---|
| `groupByFirstLetter` | `Collectors.groupingBy(w -> w.charAt(0))` |
| `sumOfEvenSquares` | `filter(n % 2 == 0)` + `mapToInt(n*n)` + `.sum()` |
| `flattenAndDistinct` | `flatMap(List::stream)` + `collect(toCollection(LinkedHashSet::new))` |
| `topN` | `sorted(comparator.reversed())` + `limit(n)` |
| `partitionByLength` | `Collectors.partitioningBy(w -> w.length() >= threshold)` |
| `frequencyMap` | `Collectors.groupingBy(e -> e, Collectors.counting())` |
| `joinWithSeparator` | `Collectors.joining(separator, prefix, suffix)` |

### OptionalExercise — przegląd metod

| Metoda | Kluczowa operacja |
|---|---|
| `findFirstAdult` | `stream().filter(age >= 18).findFirst()` |
| `mapToEmail` | `optional.map(User::getEmail)` |
| `getNameOrDefault` | `optional.map(User::getName).orElse(default)` |
| `findUserEmail` | `stream().filter(name).findFirst().flatMap(u -> ofNullable(u.getEmail()))` |

## Wnioski

- **`flattenAndDistinct` a kolejność** — `distinct()` na Stream nie gwarantuje kolejności dla równoległych streamów, ale `LinkedHashSet` gwarantuje encounter order dla sekwencyjnych. Wybór `collect(toCollection(LinkedHashSet::new))` zamiast `stream().distinct()` jest bardziej przewidywalny i testowalny przez `containsExactly`.
- **`partitioningBy` zawsze zwraca obie grupy** — w odróżnieniu od `groupingBy`, `Collectors.partitioningBy` zawsze zwraca mapę z kluczami `true` i `false`, nawet jeśli jedna z grup jest pusta. Ważne dla edge case'ów w testach.
- **`topN` a duplikaty** — `sorted().limit(n)` nie deduplikuje. Jeśli potrzeba top-N unikalnych, trzeba dodać `distinct()` przed `limit`.
- **`flatMap(ofNullable)` w `findUserEmail`** — zamiast `.map(...).filter(Optional::isPresent).map(Optional::get)` lepiej użyć `.flatMap(u -> Optional.ofNullable(u.getEmail()))`. Czytelniej i bezpieczniej gdy pole może być null.
- **`mapToEmail` vs `findUserEmail`** — rozróżnienie demonstrując dwa wzorce: `map` (transformacja wartości w Optional) vs `flatMap` (łączenie operacji, które same zwracają Optional, unikanie Optional<Optional<T>>).
- **`orElse` vs `orElseGet`** — dla prostych wartości domyślnych (String literal) `orElse` jest w porządku. Gdy tworzenie wartości domyślnej jest kosztowne, `orElseGet(Supplier)` jest leniwe i preferowane.
- **Stream a puste listy** — wszystkie metody poprawnie obsługują pustą listę: `stream()` na pustej liście zwraca pusty Stream, kolektory zwracają puste kolekcje, `findFirst()` zwraca `Optional.empty()`.
