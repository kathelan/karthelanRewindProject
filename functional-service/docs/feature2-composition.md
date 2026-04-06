# Feature 2: Kompozycja funkcji i predykatów

## Architektura

Trzy klasy statycznych metod narzędziowych w pakiecie `feature2` oraz pomocniczy rekord:

| Klasa / typ | Opis |
|---|---|
| `FunctionComposition` | Pipelines, matematyczne złożenie, Optional mapping |
| `PredicateComposition` | Negacja, AND, OR, NONE kombinacje |
| `ConsumerComposition` | Łańcuchy consumerów, warunkowe łańcuchy |
| `ConditionalAction<T>` | Record — para `(Predicate<T>, Consumer<T>)` |

### ConditionalAction — record

```java
public record ConditionalAction<T>(Predicate<T> condition, Consumer<T> action) {}
```

Record jest tutaj idealny: niezmienialny kontener dwóch powiązanych wartości, bez boilerplate. Używany przez `ConsumerComposition.conditionalChain`.

## Wnioski

### andThen vs compose — kluczowy gotcha

To najczęstsze źródło błędów przy kompozycji funkcji:

```
f.andThen(g)  →  g(f(x))  — f first, g second (lewa→prawa)
f.compose(g)  →  f(g(x))  — g first, f second (matematyczny zapis)
```

`FunctionComposition.compose(outer, inner)` odzwierciedla matematyczny zapis: `outer ∘ inner = outer(inner(x))`. Parametry są nazwane `outer` i `inner` celowo, by wymusić świadomość kolejności.

Test weryfikuje to eksperymentalnie: dla `times2 ∘ plus3` i `plus3 ∘ times2` wyniki są różne (dla `x=4`: 14 vs 11).

### buildPipeline — reduce z Function.identity()

```java
functions.stream().reduce(Function.identity(), Function::andThen)
```

`Function.identity()` jest neautralnym elementem dla `andThen` (jak 0 dla dodawania). Pusta lista daje identity — wartość nie zmieniona. To wzorzec "monoid na funkcjach".

### PredicateComposition — noneMatch jako NOT(anyMatch)

`noneMatch` jest zaimplementowany jako `anyMatch(predicates).negate()`, a nie jako osobna redukcja. To DRY — unika duplikacji logiki OR. Gotcha: `noneMatch([])` zwraca `true` bo `anyMatch([]) = false`, a `NOT false = true` — matematycznie poprawne.

### ConsumerComposition.chain — no-op identity

`Consumer` nie ma naturalnego identity jak `Function.identity()`. Tworzymy go ręcznie jako `ignored -> {}`. To lambda wymagana przy `reduce` gdy lista jest pusta. Alternatywa: `Optional.reduce` i osobna obsługa pustej listy.

### conditionalChain — brak short-circuit

`conditionalChain` przechodzi przez **wszystkie** akcje dla każdego elementu — brak `break`. To umyślne: wiele warunków może być jednocześnie spełnionych (np. liczba może być jednocześnie parzysta i dodatnia). Gdyby chcieć "first match wins", wystarczyłoby dodać `return` po pierwszym dopasowaniu.
