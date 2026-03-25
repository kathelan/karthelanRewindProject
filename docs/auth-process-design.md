# Auth Process Design

## Aktorzy

| Aktor | Opis |
|---|---|
| **Client** | Frontend / API consumer |
| **auth-service** | Backend — zarządza cyklem życia procesu autoryzacji |
| **MobilePushService** | Zewnętrzna usługa SOAP — wysyła push na urządzenie mobilne |

---

## State Machine

```
                          ┌──────────┐
                          │ PENDING  │
                          └────┬─────┘
                               │
          ┌────────────┬───────┴────────┬──────────────┐
          │            │                │              │
     scheduler    backend poll     client DELETE   nowy init
     (timeout)    → APPROVED       → cancel        dla usera
          │        → REJECTED          │              │
          ▼            │               ▼              ▼
       EXPIRED      APPROVED /      CANCELLED       CLOSED
                    REJECTED
```

### Terminalne stany

| Stan | Kto ustawia | Powód |
|---|---|---|
| `APPROVED` | auth-service (po poll MobilePushService) | user zatwierdził na telefonie |
| `REJECTED` | auth-service (po poll MobilePushService) | user odrzucił na telefonie |
| `CANCELLED` | client | client anulował proces |
| `EXPIRED` | scheduler | brak akcji w ciągu X minut |
| `CLOSED` | system | nowy init dla tego samego usera — stary zamknięty |

---

## API

```
GET    /auth/capabilities/{userId}   — sprawdź dostępne metody auth usera
POST   /process/init                 — zainicjuj proces, zwraca processId
GET    /process/{id}/status          — sprawdź stan procesu (client polluje)
DELETE /process/{id}/cancel          — anuluj proces
```

> Brak endpointu `/complete` — stan zmienia auth-service po otrzymaniu wyniku z MobilePushService.

---

## Diagram sekwencji

### Happy Path (APPROVED)

```
Client                   auth-service              MobilePushService (SOAP)
  │                           │                              │
  │── GET /capabilities ─────▶│                              │
  │                           │── getUserCapabilities() ────▶│
  │                           │◀─ {methods: [PUSH, SMS]} ───│
  │◀─ {methods: [PUSH]} ─────│                              │
  │                           │                              │
  │── POST /init ────────────▶│                              │
  │                           │── sendPush(userId, procId) ─▶│
  │                           │◀─ {deliveryId, SENT} ───────│
  │                           │  state: PENDING              │
  │◀─ {processId} ───────────│                              │
  │                           │                              │
  │── GET /status ───────────▶│                              │
  │◀─ {PENDING} ─────────────│                              │
  │                           │                              │
  │  (polling co 3s)          │  scheduler co 3s             │
  │                           │── GET /push/{delivId}/status▶│
  │                           │◀─ {APPROVED} ───────────────│
  │                           │  state: APPROVED             │
  │                           │  Spring Event → audit log    │
  │                           │  Spring Event → expiry token │
  │                           │                              │
  │── GET /status ───────────▶│                              │
  │◀─ {APPROVED} ────────────│                              │
```

### Cancel Flow

```
Client                   auth-service              MobilePushService (SOAP)
  │                           │                              │
  │── DELETE /cancel ────────▶│                              │
  │                           │  state: CANCELLED            │
  │                           │  Spring Event → audit log    │
  │◀─ 204 ───────────────────│                              │
```

### Expired Flow

```
Client                   auth-service              MobilePushService (SOAP)
  │                           │                              │
  │                           │  scheduler (co 1min)         │
  │                           │  sprawdza PENDING > 5min     │
  │                           │  state: EXPIRED              │
  │                           │  Spring Event → audit log    │
  │                           │                              │
  │── GET /status ───────────▶│                              │
  │◀─ {EXPIRED} ─────────────│                              │
```

### Closed Flow (nowy init gdy PENDING już istnieje)

```
Client                   auth-service              MobilePushService (SOAP)
  │                           │                              │
  │── POST /init ────────────▶│                              │
  │  (ma już PENDING)          │  stary proc → CLOSED         │
  │                           │  (transakcja atomowa)        │
  │                           │── sendPush(userId, procId2) ▶│
  │                           │◀─ {deliveryId2, SENT} ───────│
  │                           │  nowy proc → PENDING         │
  │◀─ {processId2} ──────────│                              │
```

### Rejected Flow

```
Client                   auth-service              MobilePushService (SOAP)
  │                           │                              │
  │  (polling co 3s)          │  scheduler co 3s             │
  │                           │── GET /push/{delivId}/status▶│
  │                           │◀─ {REJECTED} ───────────────│
  │                           │  state: REJECTED             │
  │                           │  Spring Event → audit log    │
  │                           │                              │
  │── GET /status ───────────▶│                              │
  │◀─ {REJECTED} ────────────│                              │
```

---

## Decyzje architektoniczne

### Dlaczego polling zamiast callback

Callback wymaga żeby MobilePushService wiedział **komu oddzwonić** — przy wielu
integrujących się aplikacjach wymaga rejestracji `clientId → callbackUrl`.
Polling po stronie auth-service eliminuje ten problem:
- auth-service sam odpytuje o status swoich procesów
- MobilePushService nie musi znać adresów konsumentów
- Prościej, bezpieczniej, brak ryzyka SSRF

### Dlaczego brak endpointu /complete po stronie klienta

Stan procesu zmienia wyłącznie auth-service na podstawie odpowiedzi
MobilePushService. Klient nie może sfabrykować zatwierdzenia.

### Concurrency — CLOSED

`init` gdy istnieje PENDING musi być atomowy:
```sql
UPDATE process SET state='CLOSED' WHERE userId=? AND state='PENDING';
INSERT INTO process (id, userId, state) VALUES (?, ?, 'PENDING');
```
Obie operacje w jednej transakcji — zabezpieczenie przed race condition.

### Spring Events — wewnętrzne side effects

Zmiany stanu publikują eventy wewnątrz auth-service:
- `AuthProcessApprovedEvent` → audit log, expiry token
- `AuthProcessExpiredEvent` → audit log
- `AuthProcessCancelledEvent` → audit log

Eventy nie wychodzą poza auth-service (brak Kafka na tym etapie).

---

## Mapa mikroserwisów

```
karthelan-rewind/
├── core-common
├── soap-service
│   ├── soap-service-api      (rozszerzony — mobile-push.xsd)
│   ├── soap-service-rest     (rozszerzony — MobilePushEndpoint, repozytoria)
│   └── soap-service-client   (rozszerzony — MobilePushClient)
├── user-service
│   ├── user-service-api
│   └── user-service-rest
└── auth-service              ← NOWY
    ├── auth-service-api
    └── auth-service-rest
```

---

## soap-service — rozszerzenia

### mobile-push.xsd (nowy plik, namespace: http://kathelan.pl/soap/push)

**Enums:** `AuthMethod` (PUSH, SMS, BIOMETRIC), `SendStatus` (SENT, FAILED, NO_DEVICE),
`PushStatus` (PENDING, APPROVED, REJECTED, EXPIRED), `ErrorCode` (USER_NOT_FOUND,
USER_INACTIVE, DELIVERY_NOT_FOUND, INTERNAL_ERROR)

**Operacje:**
- `getUserCapabilities(userId)` → `{userId, active, authMethods[]}`
- `sendPush(userId, processId)` → `{deliveryId, sendStatus}`
- `getPushStatus(deliveryId)` → `{deliveryId, pushStatus}`

### Repository pattern

```
PushRepository (interface)
  ├── save(PushRecord)
  ├── findByDeliveryId(deliveryId)
  ├── updateStatus(deliveryId, PushStatus)
  └── findPendingOlderThan(duration)

InMemoryPushRepository (@Profile("!sql"))   ← ConcurrentHashMap
JpaPushRepository      (@Profile("sql"))    ← przyszłość

CapabilitiesRepository (interface)
  └── findByUserId(userId)

InMemoryCapabilitiesRepository (@Profile("!sql"))   ← pre-seedowane dane
```

### Simulator

```
SimulatorController (@Profile("simulator"))
  POST /simulator/push/{deliveryId}/approve  → updateStatus(id, APPROVED)
  POST /simulator/push/{deliveryId}/reject   → updateStatus(id, REJECTED)
```

Aktywowany profilem `simulator` — operuje na `PushRepository` interface,
nie wie nic o in-memory. Na produkcji niewidoczny.

---

## Strategia testów

| Warstwa | Co testujemy |
|---|---|
| **Unit — State Machine** | każde przejście, guard conditions, blokada terminali |
| **Unit — domenowe** | reguły biznesowe, custom exceptions |
| **Kontraktowe (CDC)** | auth-service ↔ MobilePushService (SOAP contract) |
| **Kontraktowe (CDC)** | client ↔ auth-service (REST contract) |
| **Security** | IDOR — cudzy processId, expired process, double-cancel |
| **Concurrency** | równoległy init gdy PENDING → tylko jeden PENDING, stary CLOSED |
| **E2E** | pełny flow: capabilities → init → poll → approved → status |
| **E2E** | cancel flow, expired flow, closed flow |