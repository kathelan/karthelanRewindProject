# Analiza wydajności indeksów PostgreSQL — tabela auth_process

## Konfiguracja testów

- Tabela: `auth_process`, **6 000 000 wierszy**, 100 000 unikalnych użytkowników (60 wierszy/użytkownik)
- 6 statusów rozłożonych równomiernie: PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED, CLOSED (~10 wierszy/użytkownik/status)
- Środowisko: PostgreSQL 16 (TestContainers), Java 21, HikariCP
- Pomiary: czas ścienny w ms + liczba bloków z `EXPLAIN (ANALYZE, BUFFERS)`

---

## Test 1 — UPDATE z indeksem vs bez indeksu (user_id)

Pojedynczy UPDATE per użytkownik: `WHERE user_id=? AND status='PENDING'`

| Scenariusz | Czas/UPDATE | Odczytane bloki | Przyspieszenie |
|---|---|---|---|
| Bez indeksu | ~350 ms | ~55 000 | — |
| `INDEX ON user_id` | ~0.4 ms | ~64 | **853x czas, 860x bloki** |

**Wniosek:** Bez indeksu na tabeli 6M wierszy każdy UPDATE wymusza pełny sekwencyjny skan
(~55 000 bloków × 8 KB ≈ 430 MB odczytane na jedno zapytanie). Dodanie indeksu na `user_id`
redukuje to do 64 bloków i przyspiesza zapytanie ~850 razy.

---

## Test 2 — Masowy UPDATE schedulera: indeks złożony vs brak indeksu

Masowy UPDATE: `WHERE status='PENDING' AND expires_at < NOW()` — 600 000 wierszy (10% tabeli)

| Scenariusz | Czas | Odczytane bloki | Przyspieszenie |
|---|---|---|---|
| Bez indeksu złożonego | ~8 000 ms | ~55 000 | — |
| `INDEX ON (status, expires_at)` | ~4 200 ms | ~580 | **95x bloków, ~1.9x czas** |

**Dlaczego przyspieszenie czasowe jest mniejsze niż blokowe:**
Przy 600k wierszy dominuje koszt zapisu. Odczytanie 95x mniej bloków oszczędza I/O po stronie
SELECT, ale każdy z 600k wierszy nadal wymaga zapisu do WAL + aktualizacji strony na stercie.
Przepustowość zapisu staje się wąskim gardłem, nie skanowanie.

**Kluczowa zasada:** Indeks przyspiesza klauzulę WHERE (znalezienie wierszy). Nie może
przyspieszyć zapisu dopasowanych wierszy. Przy masowych operacjach (>10% tabeli) korzyść
indeksu dla UPDATE maleje.

---

## Test 3 — Pojedynczy UPDATE: indeks user_id vs złożony vs subquery

Każdy UPDATE dotyka dokładnie **1 wiersza** (filtrowanie po unikalnym `delivery_id`), ale klauzula
WHERE zawiera `user_id` i `status`. Testowano dla N ∈ {100, 1000, 10 000, 100 000}.

### Wyniki (EXPLAIN ANALYZE — bloki)

| Strategia indeksu | Typ skanu | Bloków odczytanych na zapytanie |
|---|---|---|
| `INDEX ON user_id` | Bitmap Heap Scan | 63 (60 heap + 3 index) |
| `INDEX ON (user_id, status)` | Index Scan | 23 (20 heap + 3 index) |
| złożony + subquery `WHERE id IN (...)` | Index Scan | 23 (identycznie jak B) |

Różnica w blokach: **2.74x mniej odczytów** przy indeksie złożonym — stała dla każdego N.

### Wyniki (czas ścienny)

| N | A: indeks user_id | B: złożony | C: subquery | B vs A czas |
|---|---|---|---|---|
| 100 | — | — | — | ~1.0x (szum) |
| 1 000 | 322 ms | 281 ms | 291 ms | 1.15x |
| 10 000 | 2 933 ms | 3 041 ms | 2 731 ms | ~1.0x (szum) |
| 100 000 | 28 516 ms | 25 929 ms | 28 630 ms | 1.10x |

Czas skaluje się **liniowo z N** (~0.29 ms/update stałe), co potwierdza:
- Wąskim gardłem jest RTT do bazy + zapis WAL (stały per wiersz), nie wyszukiwanie przez indeks
- Różnica czasowa między strategiami A/B/C to szum pomiarowy w tej skali

**Dlaczego złożony wygrywa na blokach, ale nie na czasie (lokalny Docker):**
Lokalny Docker RTT ≈ 0.1–0.2 ms dominuje. Na prawdziwej bazie z opóźnieniem dysku (np. EBS ~1 ms/IO),
2.74x mniej bloków = ~40 ms oszczędności na zapytanie → złożony staje się wymiernie szybszy.

### Subquery (C) — bez sensu

`WHERE id IN (SELECT id FROM auth_process WHERE ...)` generuje identyczny plan wykonania
co bezpośredni WHERE. Zero korzyści, więcej złożoności. Nie używać.

---

## Kiedy UPDATE zaczyna być problemem przy rosnącej tabeli?

### Koszt wyszukiwania przez indeks — O(log N), praktycznie stały

Współczynnik rozgałęzienia B-tree w PostgreSQL ≈ 300:

```
N =         6 000 000  →  3 poziomy B-tree  =  3 odczyty stron
N =       600 000 000  →  4 poziomy B-tree  =  4 odczyty stron
N = 60 000 000 000     →  5 poziomów B-tree =  5 odczytów stron
```

Żeby podwoić koszt wyszukiwania (3 → 6 poziomów), potrzeba **729 miliardów wierszy**.
Głębokość indeksu nie jest problemem.

### Koszt zapisu — stały, niezależny od N

Każdy UPDATE zawsze modyfikuje te same struktury:

```
WAL:              ~2 zapisy stron  (stara + nowa wersja wiersza)
strona na stercie: 1 zapis strony
INDEX user_id:     1 zapis strony
INDEX złożony:     1 zapis strony
INDEX PK (UUID):   1 zapis strony
─────────────────────────────────
łącznie:          ~6 zapisów stron — nie rośnie z rozmiarem tabeli
```

### Prawdziwy próg wydajności: bloat MVCC

Każdy UPDATE zostawia **martwy wiersz** (stara wersja w MVCC). autovacuum uruchamia się gdy:

```
martwe_wiersze > 50 + 0.2 × żywe_wiersze

Dla 6M wierszy: próg = 1 200 050 martwych wierszy
```

Dni do wyzwolenia autovacuum (domyślna konfiguracja):

```
10 000 aktualizacji/dzień  →  120 dni
100 000 aktualizacji/dzień →   12 dni
500 000 aktualizacji/dzień →    2.4 dnia
```

Jeśli autovacuum nie nadąża (długie transakcje, obciążony dysk, mało workerów),
tabela **fizycznie rośnie na dysku** mimo stałej liczby wierszy → współczynnik trafień cache spada
→ czas UPDATE skacze.

### Prawdziwy próg wydajności: wyczerpanie shared_buffers

Gdy tabela przekroczy dostępną RAM + `shared_buffers`, strony są zimne przy cache miss:

| Stan cache | I/O per UPDATE | Czas na SSD | Czas na EBS (1ms/IO) |
|---|---|---|---|
| Gorący cache (lokalnie) | 0 odczytów z dysku | ~0.3 ms | ~0.3 ms |
| Zimny cache, indeks złożony | 23 odczyty z dysku | ~1–3 ms | ~23 ms |
| Zimny cache, indeks user_id | 63 odczyty z dysku | ~3–8 ms | ~63 ms |

**Próg alarmowy:** Monitoruj `pg_stat_user_tables.n_dead_tup / n_live_tup`.
Gdy przekroczy 20–30%, autovacuum nie nadąża.

---

## Podsumowanie — dobór indeksu

| Wzorzec zapytania | Zalecany indeks |
|---|---|
| `WHERE user_id = ?` tylko | `INDEX ON (user_id)` |
| `WHERE user_id = ? AND status = ?` | `INDEX ON (user_id, status)` — 2.74x mniej bloków |
| Scheduler: `WHERE status = ? AND expires_at < ?` | `INDEX ON (status, expires_at)` — 95x mniej bloków |
| Subquery `WHERE id IN (SELECT id WHERE ...)` | Brak korzyści vs bezpośredni WHERE — unikać |

**Konkluzja:** Indeks złożony zawsze redukuje koszt I/O. Korzyść czasowa ujawnia się przy
prawdziwym opóźnieniu dysku lub współbieżnym obciążeniu — na lokalnym Dockerze jest maskowana
przez szum RTT.
