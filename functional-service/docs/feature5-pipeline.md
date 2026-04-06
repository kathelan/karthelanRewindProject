# Feature 5: Pipeline

## Architektura

Jedna klasa w pakiecie `pl.kathelan.functional.feature5`:

| Element | Rola |
|---|---|
| `Pipeline<T>` | Immutable fluent pipeline builder — serializuje kroki (map / filter) i aplikuje je eagerly przy wykonaniu |
| `Step<T>` (private record) | Para `(StepKind, Function<T,T>, Predicate<T>)` — jeden krok w pipeline |
| `StepKind` (private enum) | `MAP` lub `FILTER` — rozróżnienie przy przetwarzaniu |

### Przepływ danych (single value)

```
Pipeline.<String>of()      // pusta lista kroków
    .map(String::trim)     // nowy Pipeline z jednym krokiem MAP
    .filter(s -> ...)      // nowy Pipeline z dwoma krokami
    .execute("  hello  ")  // iteracja po krokach → Optional<String>
```

Każde `.map()` i `.filter()` tworzy **nowy** `Pipeline` z kopią kroków (`List.copyOf`) + nowym krokiem. Oryginalny obiekt nie jest modyfikowany.

### Przepływ danych (lista)

```
.executeAll(List.of(...))
```

Iteruje po elementach, dla każdego wywołuje `applySteps(input)` i zbiera niepuste Optionale do wynikowej listy. Elementy odfiltrowane są pomijane.

### Zmiana typu — `mapTo(Function<T, R>)`

Najtrudniejszy element. Ponieważ lista `Step<T>` jest typowana przez `T`, zmiana na `R` wymaga stworzenia nowego `Pipeline<R>`. Implementacja:
1. Robi snapshot obecnego pipeline'u `T`.
2. Tworzy `Function<Object, Object>` — kombinację wszystkich istniejących kroków T + mapera T→R.
3. Otwiera nowy `Pipeline<R>` z jednym krokiem MAP trzymającym tę kombinację.

## Wnioski

- **Immutability wymaga defensive copy** — każdy `addRule`/`map`/`filter` musi kopiować listę kroków. `List.copyOf` jest wystarczające i tańsze od `new ArrayList` + `addAll` gdy tylko odczytujemy.
- **`mapTo` jest nieintuicyjny** — zmiana typu w immutable, typowanej liście kroków wymaga "złożenia" istniejących kroków w jedną funkcję i otwarcia nowego pipeline'u. Gdyby kroki były przechowywane jako `Function<Object, Object>`, byłoby prościej, ale tracona jest type safety.
- **Eager vs Lazy** — wykonanie jest eager (przy `execute`/`executeAll`), nie lazy jak Stream. Dla uproszczenia implementacji to dobry kompromis; lazy wymagałoby opóźnionych Iteratorów lub Spliteratorów.
- **`applySteps` zwraca `Optional`** — jeśli filtr odrzuci wartość, metoda zwraca `Optional.empty()` bez dalszego przetwarzania. To naturalne short-circuiting na poziomie jednego elementu.
- **Null safety** — jeśli maper zwróci `null` (np. w `mapTo`), pipeline poprawnie zwraca `Optional.empty()`, bo `Optional.of(null)` rzuciłoby NPE — dlatego sprawdzamy `current == null` przed opakowaniem.
- **Różnica z `FunctionComposition.buildPipeline`** (Feature 2) — tamten przyjmuje gotową listę funkcji; Pipeline buduje kroki fluent API i obsługuje filtry, nie tylko mapy.
