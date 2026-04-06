# Feature 1: Podstawy — Supplier, Consumer, Function, Predicate

## Architektura

Cztery klasy statycznych metod narzędziowych w pakiecie `feature1`:

| Klasa | Interfejs | Rola |
|---|---|---|
| `SupplierExercise` | `Supplier<T>` | Produkcja wartości (lazy init, default, generowanie list) |
| `ConsumerExercise` | `Consumer<T>`, `BiConsumer<K,V>` | Efekty uboczne (iteracja, łączenie akcji) |
| `FunctionExercise` | `Function<T,R>` | Transformacje z nowym typem wyjściowym |
| `PredicateExercise` | `Predicate<T>` | Filtrowanie, liczenie, partycjonowanie |

Żadna klasa nie jest Spring beanem — wszystkie metody są `static` i nie mają stanu.

### Przepływ danych

```
Supplier  →  wartość (produkuje)
Consumer  →  void   (konsumuje)
Function  →  R      (transformuje T→R)
Predicate →  boolean (testuje)
```

## Wnioski

### Supplier — memoizacja
`lazy()` używa `AtomicBoolean` + `AtomicReference` zamiast zwykłego pola, bo lambdy nie mogą mutować zmiennych nieatomowych. Ważne: nawet gdy supplier zwróci `null`, flaga `computed` jest ustawiana na `true`, co zapobiega ponownemu wywołaniu — bez tego null nie byłby cachowany.

### Consumer — andThen vs własna implementacja
`combine()` deleguje do `first.andThen(second)`. Metoda `andThen` jest dostępna na wszystkich interfejsach funkcyjnych jako metoda domyślna. Kolejność jest lewa→prawa, co jest intuicyjne. `BiConsumer` naturalnie pasuje do `Map.forEach`.

### Function — tryApply i bezpieczne parsowanie
`tryApply()` chwyta **wszystkie** wyjątki (`Exception`), a nie tylko `RuntimeException`. To celowy wybór: `NumberFormatException` jest `RuntimeException`, ale np. parsery dat mogą rzucać checked wyjątki. Zwraca `Optional.empty()` dla null inputu, bo `Integer.parseInt(null)` rzuca `NumberFormatException`.

### Function — toMap i duplikaty kluczy
`Collectors.toMap` **rzuca** `IllegalStateException` przy duplikacie klucza (inaczej niż `groupingBy`). To częsty gotcha — jeśli chcemy merge'ować duplikaty, trzeba przekazać trzeci argument (merge function).

### Predicate — allOf z pustą listą
`allOf(emptyList)` zwraca `p -> true` (vacuous truth). `anyOf(emptyList)` zwraca `p -> false`. To spójne z matemtyką: AND na zbiorze pustym = true (identity dla AND), OR na zbiorze pustym = false (identity dla OR).

### Predicate — partition
`Collectors.partitioningBy` zawsze zwraca mapę z **obu** kluczami (`true` i `false`), nawet gdy jedna z list jest pusta. Inaczej niż `groupingBy`, który nie tworzy pustych grup.
