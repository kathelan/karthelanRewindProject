# Analiza wydajności JPA + PostgreSQL — wnioski z 9 faz testów

Środowisko: PostgreSQL 16, Spring Boot 3.4, Hibernate 6.6, Java 21, Docker lokalny.
Dane: 600 000 wierszy, 10 000 użytkowników (60 wierszy/user, 10/status).
N = 1 000 operacji per faza.

```
docker-compose -f db-performance/docker-compose.yml up -d
mvn test -pl db-performance -Dgroups=performance -Dexcluded.test.groups=""
```

---

## Wyniki zbiorcze

| Faza | Scenariusz | Czas | Wynik |
|------|------------|------|-------|
| 1 vs 4 | `@Modifying` bez indeksu vs z indeksem | 34 703 ms → 811 ms | **43x szybciej** |
| 2 vs 5 | `save()` INSERT bez indeksu vs z indeksem | 816 ms → 649 ms | cache warm — brak różnicy |
| 6a vs 6b | 1000 transakcji vs 1 transakcja | 703 ms → 430 ms | **1,6x lokalnie, ~50–100x prod** |
| 4 vs 7 | Full composite vs partial index | 811 ms vs 712 ms | partial **9,5x mniejszy** |
| 8a vs 8b | 1000×`save()` vs `saveAll(1000)` | 557 ms → 27 ms | **20x szybciej** |
| 9a vs 9b | N+1 (100×`findById`) vs `findAllById` | 103 ms → 23 ms | **100 SQL → 1 SQL** |

---

## Lekcja 1 — Indeks na kolumnach WHERE to absolutne minimum

Bez indeksu na `(user_id, status)` każdy UPDATE robi **Seq Scan** całej tabeli:

```
EXPLAIN bez indeksu:
  Seq Scan on auth_process
  Filter: (user_id='user_0' AND status='PENDING')
  Rows Removed by Filter: 599 980   ← 99,997% pracy zmarnowane
  Buffers: shared hit=8 713         ← ~68 MB odczytane na 1 UPDATE
  Execution Time: 40,684 ms
```

Z indeksem `(user_id, status)`:

```
EXPLAIN z indeksem:
  Bitmap Heap Scan on auth_process
  Bitmap Index Scan on idx_jpa_user_status
  Index Cond: (user_id='user_1000' AND status='PENDING')
  Buffers: shared hit=209 read=3    ← 41x mniej bloków
  Execution Time: 0,270 ms          ← 150x szybsze wykonanie
```

**Wynik:** 34 703 ms → 811 ms (**43x**) dla 1000 operacji.

**Zasada:** każda kolumna w klauzuli `WHERE` używana przez UPDATE/SELECT musi mieć indeks,
jeśli zapytanie nie dotyka >10–20% tabeli.

**Na produkcji jest gorzej** — lokalny Docker trzyma wszystko w RAM (shared_buffers).
Na RDS z EBS każdy brakujący blok w cache = odczyt z dysku ≈ 1 ms:
- 8 713 bloków × 1 ms = **~8,7 sekundy** na jedno zapytanie
- Z indeksem: 212 bloków × 1 ms = **~0,2 sekundy**
- Realny speedup na produkcji: **40–200x**

---

## Lekcja 2 — `@Modifying` vs `save()` — kiedy używać którego

```java
// @Modifying — jeden SQL UPDATE, brak SELECT, brak Hibernate overhead
@Modifying
@Transactional
@Query("UPDATE AuthProcess a SET a.status = :new, a.updatedAt = :now WHERE a.userId = :uid AND a.status = :old")
int updateByUserIdAndStatus(...);

// save() — najpierw SELECT, potem UPDATE po PK (dwa round-tripy)
repository.findFirstByUserIdAndStatus(userId, PENDING)
    .ifPresent(e -> { e.setStatus(APPROVED); repository.save(e); });
```

| Strategia | SQL | Kiedy używać |
|-----------|-----|--------------|
| `@Modifying` | 1× UPDATE WHERE | scheduler, bulk, prosta zmiana statusu |
| `save()` | 1× SELECT + 1× UPDATE WHERE id=? | logika domenowa, eventy, `@Version` |

`save()` po `findById` jest zawsze szybki dla UPDATE po PK — PK jest zawsze zaindeksowany.
Różnica między strategiami sprowadza się do kosztu SELECT w `findFirst`.

---

## Lekcja 3 — Overhead transakcji: BEGIN/COMMIT kosztuje

Każde wywołanie metody `@Transactional` = jedna transakcja = jeden `BEGIN` + jeden `COMMIT`.
`COMMIT` wymusza zapis WAL na dysk (fsync) — na produkcji to 0,5–5 ms per commit.

```java
// ŹLE — 1000 transakcji, 1000 × fsync
for (String userId : userIds) {
    service.updateByModifying(userId, PENDING, APPROVED);  // każde wywołanie = BEGIN/COMMIT
}

// DOBRZE — 1 transakcja, 1 × fsync
service.updateAllInOneTransaction(userIds, PENDING, APPROVED);
```

```java
@Transactional
public void updateAllInOneTransaction(List<String> userIds, ...) {
    LocalDateTime now = LocalDateTime.now();
    for (String userId : userIds) {
        repository.updateByUserIdAndStatus(userId, oldStatus, newStatus, now);
    }
    // jeden COMMIT na końcu metody
}
```

**Wynik lokalnie:** 703 ms → 430 ms (1,6x) — Docker używa RAM, fsync tani.
**Na produkcji (EBS):** 1000 × 2 ms (fsync) = 2 sekundy samych commitów.
Jeden commit = oszczędność 2 sekund → **realny speedup 5–50x**.

**Zasada:** jeśli piszesz pętlę w serwisie i każda iteracja zmienia dane —
przenieś `@Transactional` na metodę zawierającą całą pętlę, nie na metodę per-iterację.

---

## Lekcja 4 — Partial index: mały indeks, ta sama prędkość

Gdy zapytanie zawsze filtruje po konkretnej wartości (np. `status='PENDING'`),
możesz zbudować indeks tylko na tych wierszach:

```sql
-- Full composite: indeksuje 600 000 wierszy
CREATE INDEX idx_full ON auth_process(user_id, status);

-- Partial: indeksuje tylko ~100 000 wierszy PENDING (1/6 tabeli)
CREATE INDEX idx_partial ON auth_process(user_id) WHERE status = 'PENDING';
```

**Rozmiar:**
```
Full composite (user_id, status):        5 216 kB
Partial        (user_id) WHERE PENDING:   552 kB   ← 9,5x mniejszy
```

**Prędkość zapytania:** identyczna (0,81 ms vs 0,71 ms) — oba używają Bitmap Index Scan.

**EXPLAIN z partial index:**
```
Bitmap Index Scan on idx_partial_pending
Index Cond: (user_id='user_4000')       ← status sprawdzany przez predykat, nie kolumnę
Buffers: shared read=2                  ← 3 bloki mniej niż pełny composite
```

**Kiedy używać partial indexu:**
- Scheduler: `WHERE status = 'PENDING' AND expires_at < NOW()`
- Soft-delete: `WHERE deleted = false`
- Flagi: `WHERE is_active = true`

**Korzyści:**
1. Szybszy INSERT — baza aktualizuje indeks tylko gdy nowy wiersz spełnia predykat
2. Mniejszy indeks mieści się w cache RAM (mniej cache miss)
3. Szybszy VACUUM — mniej wpisów do przetworzenia

---

## Lekcja 5 — `saveAll()` vs pętla `save()`: 20x szybciej

```java
// ŹLE — 1000 round-tripów, 1000 transakcji
for (Entity e : entities) {
    repository.save(e);
}

// DOBRZE — 2 round-tripy (batch_size=500), 1 transakcja
repository.saveAll(entities);
```

Konfiguracja w `application.properties`:
```properties
spring.jpa.properties.hibernate.jdbc.batch_size=500
spring.jpa.properties.hibernate.order_inserts=true
```

**Wynik:** 557 ms → 27 ms (**20x szybciej**).

**Dlaczego działa:**
Hibernate grupuje 500 INSERT-ów w jedno wywołanie JDBC zamiast 500 osobnych.
Sterownik PostgreSQL wysyła to jako jeden pakiet sieciowy. Zamiast 1000 round-tripów
(1000 × ~0,5 ms RTT = 500 ms overhead) — 2 round-tripy.

**Warunek konieczny — `GenerationType.UUID` (lub sekwencja):**
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)  // UUID generowany w Javie przed INSERT
private UUID id;
```
Z `GenerationType.IDENTITY` (auto-increment) Hibernate **wyłącza batching** — musi odczytać
wygenerowane ID po każdym INSERT z powrotem z bazy. Z UUID Hibernate zna ID z góry i może
pakować INSERTy w grupy.

---

## Lekcja 6 — Problem N+1: 100 zapytań zamiast 1

**Symptom:** ładujesz listę encji, a potem dla każdej oddzielnie wywołujesz powiązane dane.

```java
// ŹLE — N+1: 1 SELECT na listę + N SELECT per element
List<UUID> ids = getIds();
for (UUID id : ids) {
    repository.findById(id);   // każde wywołanie = nowy EntityManager = nowy SELECT
}

// DOBRZE — 1 SELECT z WHERE id IN (...)
repository.findAllById(ids);
```

**Wynik zmierzony:**
```
100 × findById:   103 ms  |  SQL statements: 100
findAllById(100):  23 ms  |  SQL statements:   1
```

Hibernate Statistics (`generate_statistics=true`) policzyło dokładnie: **100 vs 1 prepared statement**.

**Klasyczny N+1 przy relacjach `@OneToMany(fetch=LAZY)`:**
```java
// PROBLEM: 1 SELECT na users + N SELECT na orders (dla każdego usera osobno)
List<User> users = userRepo.findAll();
users.forEach(u -> process(u.getOrders()));  // trigger lazy load per user

// ROZWIĄZANIE A: JOIN FETCH w JPQL
@Query("SELECT u FROM User u JOIN FETCH u.orders")
List<User> findAllWithOrders();

// ROZWIĄZANIE B: @EntityGraph
@EntityGraph(attributePaths = "orders")
List<User> findAll();
```

**Jak wykryć N+1 w projekcie:**
1. Włącz `spring.jpa.show-sql=true` i szukaj powtarzających się SELECT-ów w logach
2. Włącz `hibernate.generate_statistics=true` i sprawdzaj `getPrepareStatementCount()`
3. Użyj Hibernate Hypersistence Utils lub p6spy do śledzenia zapytań

---

## Lekcja 7 — Slow query log: dwa niezależne mechanizmy

Dwa systemy logowania wolnych zapytań działają niezależnie i wzajemnie się uzupełniają.

### Hibernate SQL_SLOW (application.properties)

```properties
spring.jpa.properties.hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS=20
```

- Loguje do `org.hibernate.SQL_SLOW` na poziomie WARN.
- Śledzi **tylko zapytania SELECT** przez session event listener.
- **JPQL `@Modifying` UPDATEs omijają ten listener** — dla UPDATEs Hibernate SQL_SLOW nie odpali.
- Mierzy **czysty czas JDBC** (bez Spring/JPA overhead). Na gorącym cache Docker'a zapytanie
  bez indeksu może zwrócić wynik w <5 ms (bloki w shared_buffers → RAM), mimo że wall-clock
  aplikacji pokaże 20–40 ms (transakcja + obiekt mapping + sieć).
- **Na produkcji** (zimny EBS, I/O = 1 ms/blok) Seq Scan na 8 713 blokach → ~8 700 ms ≫ próg
  → SQL_SLOW odpali niezawodnie.

### PostgreSQL log_min_duration_statement

```sql
ALTER SYSTEM SET log_min_duration_statement = 20;  -- ms, bez restartu
SELECT pg_reload_conf();
```

- Loguje **każde zapytanie** (SELECT, UPDATE, INSERT) przekraczające próg.
- Mierzy **czas server-side** — od odebrania zapytania do wysłania odpowiedzi.
- Widoczne w logach kontenera: `docker logs postgres-perf | grep 'duration:'`
- Format: `duration: 38.123 ms  statement: UPDATE auth_process SET ...`
- Reset po sesji: `ALTER SYSTEM RESET log_min_duration_statement; SELECT pg_reload_conf();`

### Porównanie

| Mechanizm | Typ zapytań | Co mierzy | Gdzie widoczne |
|-----------|-------------|-----------|----------------|
| Hibernate SQL_SLOW | SELECT (przez Session) | Czas JDBC | Logi aplikacji (WARN) |
| PostgreSQL `log_min_duration_statement` | SELECT + UPDATE + INSERT | Czas server-side | docker logs kontenera |

**Zasada:** Na produkcji włącz oba. Hibernate SQL_SLOW wychwytuje N+1 i wolne fetchowanie encji.
PostgreSQL log wychwytuje wszystko, co dzieje się na bazie — włącznie z zapytaniami spoza ORM.

---

## Kiedy co stosować — ściągawka

| Scenariusz | Rekomendacja |
|------------|--------------|
| UPDATE po warunku (scheduler, bulk) | `@Modifying @Transactional` + indeks na WHERE |
| UPDATE z logiką domenową / eventami | `save()` — encja w Persistence Context |
| Pętla zmian w serwisie | Jeden `@Transactional` na metodę z pętlą, nie na iterację |
| Import / bulk insert | `saveAll(list)` z `batch_size` + `GenerationType.UUID` |
| Fetch po wielu ID | `findAllById(ids)`, nigdy `findById` w pętli |
| Scheduler `WHERE status='PENDING'` | Partial index `(user_id) WHERE status='PENDING'` |
| Zapytania po wielu kolumnach | Composite index na kolumnach z WHERE w tej kolejności |

---

## Wpływ indeksu na produkcji vs lokalny Docker

| Stan cache | Bloki z indeksem | Czas (SSD) | Czas (EBS 1 ms/IO) |
|------------|-----------------|------------|---------------------|
| Gorący (RAM) | 212 | ~0,3 ms | ~0,3 ms |
| Zimny, z indeksem | 212 | ~1–3 ms | **~212 ms** |
| Zimny, Seq Scan | 8 713 | ~50 ms | **~8 700 ms** |

Lokalny Docker maskuje różnice — wszystko siedzi w RAM.
Na produkcji z EBS każdy brakujący blok w cache = odczyt z dysku ≈ 1 ms.
Dlatego testy wydajnościowe na lokalnym Docker pokazują kierunek, ale nie skalę.
