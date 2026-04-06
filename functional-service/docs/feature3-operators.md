# Feature 3: Operatory — UnaryOperator i BinaryOperator

## Architektura

Dwie klasy statycznych metod narzędziowych w pakiecie `feature3`:

| Klasa | Interfejs | Opis |
|---|---|---|
| `UnaryOperatorExercise` | `UnaryOperator<T>` | Transformacje zachowujące typ: identity, chain, applyN, conditional |
| `BinaryOperatorExercise` | `BinaryOperator<T>` | Operacje na dwóch elementach tego samego typu: reduce, maxBy, minBy |

### Relacja do Function

```
UnaryOperator<T>  extends  Function<T, T>
BinaryOperator<T> extends  BiFunction<T, T, T>
```

`UnaryOperator<T>` to specjalizacja `Function<T,T>` gdzie input i output mają **ten sam typ**. Dzięki temu `andThen` i `compose` są dostępne, a lista `UnaryOperator` może być łatwo zredukowana.

## Wnioski

### UnaryOperator.chain — problem z reduce i typem

Nie można napisać:
```java
operators.stream().reduce(UnaryOperator.identity(), UnaryOperator::andThen);
```
ponieważ `andThen` zwraca `Function<T, T>`, a nie `UnaryOperator<T>` — kompilator nie upcastuje automatycznie. Rozwiązanie:

```java
.reduce(UnaryOperator.identity(),
        (acc, op) -> value -> op.apply(acc.apply(value)));
```

Tworzymy nową lambdę łączącą `acc` i `op`. Alternatywnie można rzutować: `(UnaryOperator<T>) acc.andThen(op)`.

### applyN — iteracja zamiast rekurencji

`applyN` używa pętli `for` zamiast rekurencji. Java nie ma tail-call optimization, więc rekurencja dla dużych `N` powodowałaby `StackOverflowError`. Pętla jest bezpieczna dla dowolnego `N`.

### BinaryOperator.maxBy i minBy — delegacja do JDK

`BinaryOperator.maxBy(comparator)` i `minBy(comparator)` to metody fabryczne z JDK — wrapper wokół:
```java
(a, b) -> comparator.compare(a, b) >= 0 ? a : b
```

Są naturalnym uzupełnieniem `stream().reduce()` — pozwalają znaleźć max/min bez `Optional` (w przeciwieństwie do `stream().max()`).

### BinaryOperator w reduce — identity element

Wybór identity musi być neutralny dla operacji:
- suma: `0` (dodanie 0 nie zmienia wartości)
- iloczyn: `1`
- max: `Integer.MIN_VALUE`
- min: `Integer.MAX_VALUE`
- konkatenacja: `""`

Zły wybór identity zmienia wynik dla pustej listy. Gotcha: `max(list, 0)` na liście samych ujemnych liczb zwróci `0` (identity), nie wartość z listy.

### UnaryOperator.conditional vs identity

`conditional(condition, operator)` zwraca element niezmieniony gdy warunek nie jest spełniony — to jest poprawny `UnaryOperator`. Efektywnie tworzymy "strażnika": albo transformacja, albo pass-through. Bardzo użyteczne przy pipelinach warunkowych.
