# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**karthelan-rewind** — Java 21 Spring Boot 3.4.x monorepo (`pl.kathelan`). Cel: nauka/powtórka technologii metodą TDD.

### Moduły

| Moduł | Status | Opis |
|---|---|---|
| `core-common` | GOTOWY | Generyczne narzędzia: interceptory, walidatory, base classes, ResilientCaller (CB + Retry) |
| `rest-service-parent` | GOTOWY | Wspólny parent POM dla modułów *-rest |
| `soap-service` | GOTOWY | SOAP endpoint (JAX-WS), symulator push statusów, WS-Security UsernameToken |
| `user-service` | GOTOWY | REST CRUD użytkowników, ResilientCaller owijający SOAP client |
| `auth-service` | GOTOWY | Proces autoryzacji + SSE streaming, device validation pipeline |
| `db-performance` | GOTOWY | Standalone testy wydajności PostgreSQL + Oracle z TestContainers, multi-datasource |
| `mapstruct-service` | GOTOWY | MapStruct — 65 testów GREEN (features 1-42) |

### Architektura soap-service

Profile Spring:
- `local` — in-memory repozytoria (dev/testy)
- `simulator` — `SimulatorController` REST do sterowania push statusami w testach
- brak/default — produkcja/Docker

WS-Security: message-level WSS4J UsernameToken (nie HTTP Basic) — skonfigurowane zarówno po stronie serwera (`WsSecurityConfig`) jak i klienta (`WsSecurityHandler` w `user-service-client`).

### Architektura auth-service — Device Validation Pipeline

`DeviceProcessingPipeline` — sekwencja kroków (`DeviceProcessingStep`):
1. `AccountStatusValidationStep` — sprawdza czy konto aktywne
2. `ActiveDeviceValidationStep` — filtruje urządzenia aktywne
3. `ActivationDateFilter` — odrzuca za stare urządzenia
4. `PassiveModeFilter` — pomija urządzenia w passive mode

`SseEmitterRegistry` — `ConcurrentHashMap` emitterów per processId; lifecycle hooks: completion/timeout/error.

### Architektura core-common — ResilientCaller

`ResilientCaller` = Circuit Breaker owijający Retry:
- CB liczy failure dopiero po wyczerpaniu wszystkich retries
- `RetryConfig` — exponential backoff + `excludeOn` (lista wyjątków niepowodujących retry, np. błędy walidacji)
- `CircuitBreakerRegistry` — globalny rejestr per klucz (np. nazwa klienta)

### Znane kompromisy (świadome)

- `initProcess` per-user lock: InMemory działa; przy JPA → optimistic locking + unique constraint
- IDOR protection: wymaga Bearer token auth (tracked: `@Disabled` test w `AuthSecurityTest`)
- `parallelStream` w `pollAndUpdatePushStatuses`: wspólny ForkJoinPool — przy JPA rozważyć dedykowany ExecutorService
- `InMemoryAuthProcessRepository`: switch na SQL/NoSQL = tylko nowa implementacja interfejsu

---

## Środowisko i build

### Problem z Java / Maven

Maven używa Java 25 (Homebrew `/opt/homebrew/Cellar/openjdk/25.0.2`) — **nie Java 21**.  
Lombok działa poprawnie dzięki `fork=true` + hardcoded `javac` path w root `pom.xml` (Temurin 21).

Jeśli pojawi się problem z Lombokiem: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` przed mvn.

### Komendy

```bash
# Pełny build
mvn clean install

# Build konkretnego modułu
mvn clean install -pl auth-service/auth-service-rest

# Testy (bez performance)
mvn test -pl auth-service/auth-service-rest

# Testy wydajnościowe (domyślnie wykluczone)
mvn test "-Dexcluded.test.groups=" -Dgroups=performance -pl auth-service/auth-service-rest

# Docker
docker-compose up --build
```

### Porty (Docker Compose)

- `soap-service` → 8080
- `user-service` → 8081
- `auth-service` → 8082

---

## Zasady kodowania — OBOWIĄZKOWE

### TDD

**Zawsze testy przed implementacją.** Nie pisz kodu bez testu który najpierw nie przechodzi.

### Jakość kodu

- **SOLID, DRY, KISS** — przy każdym tworzonym lub modyfikowanym kodzie
- Single Responsibility — klasy i metody mają jeden powód do zmiany
- Sensowne nazwy, czytelna struktura pakietów
- Bez over-engineeringu — abstrakcja tylko gdy uzasadniona
- Bez spekulatywnych abstrakcji — trzy podobne linie > przedwczesna abstrakcja

### Custom exceptions

Zawsze dedykowane klasy wyjątków domenowych — **nigdy** `IllegalArgumentException` / `RuntimeException` wprost.

```java
// DOBRZE
throw new UserNotFoundException(userId);
throw new UserAlreadyExistsException(email);

// ZLE
throw new IllegalArgumentException("User not found");
```

- Klasy wyjątków: pakiet `exception` w danym module
- Extends `RuntimeException` (unchecked)
- W testach: sprawdzać typ (`isInstanceOf`) i treść (`hasMessageContaining`)

### core-common — co tam trafia

Wszystko generyczne i domeny-agnostyczne → `core-common`, NIE do modułów domenowych.

Przykłady: `@InRange`, `@UniqueElements`, utility classes, wspólne interceptory, base test classes.  
Przed umieszczeniem w module domenowym zapytaj: czy to specyficzne dla tej domeny?

### Testy — adnotacje Spring

Używać `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`) — **nie** `@MockBean` (deprecated od Spring Boot 3.4).

### Lombok

Projekt używa Lomboka. Domain model w `soap-service` tymczasowo jako Java records (historyczny kompromis) — przy nowym kodzie używaj Lomboka normalnie (`@Data`, `@Builder`, `@RequiredArgsConstructor` itp.).

---

## Wzorzec: repozytoria i klienty

### Repozytoria — interface-first + profil `local`

Zawsze pisz interfejs repozytorium, a implementację zaczynaj od in-memory z `@Profile("local")`.  
JPA/SQL/NoSQL to osobna implementacja tego samego interfejsu — dodawana później bez zmiany logiki domenowej.

```java
// 1. Interfejs domenowy
public interface PushRecordRepository {
    void save(PushRecord record);
    Optional<PushRecord> findById(String id);
}

// 2. Implementacja in-memory (dev/testy)
@Repository
@Profile("local")
public class InMemoryPushRecordRepository implements PushRecordRepository { ... }

// 3. Implementacja JPA (dodawana gdy trzeba, bez zmian w domenie)
@Repository
@Profile("!local")
public class JpaPushRecordRepository implements PushRecordRepository { ... }
```

Dzięki temu: testy chodzą bez bazy, JPA to tylko nowa implementacja, domena nie wie o storage.

### Klienty — obowiązkowy circuit breaker

Circuit breaker jest zaimplementowany w `core-common`. **Każde** wywołanie zewnętrznego klienta (SOAP, REST, itp.) musi być opakowane przez `ResilientCaller` z core-common — bez wyjątków.

---

## Dokumentacja feature'ów

Dla każdego nowego feature w danym module twórz plik docs w module:

```
<moduł>/docs/<nazwa-feature>.md
```

Plik musi zawierać:
1. **Architektura** — jak feature jest zbudowany, jakie klasy/interfejsy uczestniczą, przepływ danych
2. **Wnioski** — co zadziałało, co nie, niespodziewane zachowania, gotchas (szczególnie ważne!)

Przykład: `mapstruct-service/docs/localized-order-mapper.md` opisuje features 34-42, w tym gotcha: `@ObjectFactory` jest pomijany gdy target ma `@SuperBuilder`.

---

## Code review przed commitem

Przed każdym commitem wywołaj subagenta do code review:

```
Agent(subagent_type="general-purpose", prompt="
Zrób code review zmienionych plików w tym commicie.
Sprawdź: SOLID, DRY, custom exceptions (nie generyczne), @MockitoBean (nie @MockBean),
circuit breaker przy wywołaniach klientów, interface-first dla repozytoriów.
Zwróć uwagę na błędy bezpieczeństwa i jakość testów.
Pliki: <lista zmienionych plików>
")
```

Alternatywnie użyj `/simplify` skill po zakończeniu implementacji.

---

## Struktura projektu

```
karthelan-rewind/
├── core-common/              # Generyczne narzędzia (interceptory, walidatory)
├── rest-service-parent/      # Parent POM dla *-rest modułów
├── soap-service/
│   ├── soap-service-api/     # XSD + wygenerowane klasy JAX-WS
│   ├── soap-service-client/  # Klient SOAP
│   └── soap-service-rest/    # Endpoint + SimulatorController
├── user-service/
│   ├── user-service-api/
│   ├── user-service-client/
│   └── user-service-rest/
├── auth-service/
│   ├── auth-service-api/
│   └── auth-service-rest/    # SSE, polling, ResilientCaller
├── db-performance/           # Standalone TestContainers + PostgreSQL + Oracle (multi-datasource)
├── mapstruct-service/        # MapStruct mappers (features 1-42, 65 testów)
├── docker-compose.yml
└── pom.xml                   # Root POM — wersje, pluginy, dependency management
```