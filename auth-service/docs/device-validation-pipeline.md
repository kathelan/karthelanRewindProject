# Device Validation Pipeline

## Cel

Rozszerzenie endpointu `GET /auth/capabilities/{userId}` o krokową walidację i filtrowanie urządzeń użytkownika.
Dane urządzeń przychodzą z soap-service (SOAP `getUserCapabilities`), który został rozszerzony o listę urządzeń.

---

## Architektura

### Przepływ danych

```
GET /auth/capabilities/{userId}
    │
    ▼
AuthController.getCapabilities(userId)
    │
    ▼
AuthProcessServiceImpl.getCapabilities(userId)
    │
    ├── SOAP: getUserCapabilities(userId)
    │       └─ GetUserCapabilitiesResponse { accountStatus, devices[], authMethods, ... }
    │
    ├── accountStatus == null?  →  return empty CapabilitiesResponse
    │
    └── DeviceProcessingPipeline.execute(devices)
            │
            ├── Step 1: ActiveDeviceValidationStep
            │       filtruje tylko ACTIVE
            │       jeśli pusta lista + są BLOCKED  →  throw AllDevicesBlockedException
            │       jeśli pusta lista + brak BLOCKED →  throw NoDevicesFoundException
            │
            ├── Step 2: PassiveModeFilter
            │       odrzuca urządzenia z isPassiveMode=true
            │
            └── Step 3: ActivationDateFilter
                    odrzuca urządzenia z activationDate < now()-30dni
                    (starsze niż 30 dni przechodzą)
            │
            ▼
    List<DeviceDto> — przefiltrowana lista
```

### Klasy / interfejsy

| Klasa | Pakiet | Rola |
|---|---|---|
| `DeviceProcessingStep` | `pipeline` | Interfejs — jeden krok, `List<DeviceDto> process(List<DeviceDto>)` |
| `DeviceProcessingPipeline` | `pipeline` | Orkiestrator — wykonuje kroki po kolei via `@Order` |
| `ActiveDeviceValidationStep` | `pipeline.step` | Filtr ACTIVE + walidacja błędów |
| `PassiveModeFilter` | `pipeline.step` | Filtr `isPassiveMode=false` |
| `ActivationDateFilter` | `pipeline.step` | Filtr `activationDate` > 30 dni |
| `AllDevicesBlockedException` | `exception` | Wszystkie urządzenia zablokowane |
| `NoDevicesFoundException` | `exception` | Brak jakichkolwiek urządzeń |
| `DeviceDto` | `auth-service-api/dto` | Rekord: deviceId, status, isMainDevice, isPassiveMode, activationDate |
| `DeviceStatus` | `auth-service-api/dto` | Enum: ACTIVE, BLOCKED, INACTIVE |
| `AccountStatus` | `auth-service-api/dto` | Enum: ACTIVE, BLOCKED, SUSPENDED |
| `CapabilitiesResponse` | `auth-service-api/dto` | Rozszerzone o `accountStatus` + `List<DeviceDto> devices` |

### Pattern: Pipeline + Chain of Responsibility

- `DeviceProcessingStep` — jeden interfejs, jeden krok
- `DeviceProcessingPipeline` — Spring wstrzykuje `List<DeviceProcessingStep>` posortowaną przez `@Order`
- każdy krok albo filtruje (zwraca podlistę) albo przerywa pipeline (rzuca wyjątek)

### Rozszerzenie soap-service

XSD (`mobile-push.xsd`) rozszerzone o:
- `AccountStatus` enum: `ACTIVE | BLOCKED | SUSPENDED`
- `DeviceStatus` enum: `ACTIVE | BLOCKED | INACTIVE`
- `DeviceDto` complex type: `deviceId`, `status`, `isMainDevice`, `isPassiveMode`, `activationDate`
- `getUserCapabilitiesResponse` + `accountStatus` + `devices[]`

Domain w soap-service-rest:
- `AccountStatus` enum
- `DeviceStatus` enum
- `DeviceInfo` value object (`@Value @Builder`)
- `UserCapabilities` rozszerzone o `accountStatus` + `List<DeviceInfo> devices`

---

## Wnioski

### Co zadziałało

- **Pipeline jako `List<DeviceProcessingStep>` z `@Order`** — Spring wstrzykuje posortowaną listę automatycznie. Brak ręcznego zarządzania kolejnością. Dodanie nowego kroku to tylko nowy `@Component @Order(N)`.
- **Interfejs z `userId`** — przekazanie `userId` przez cały pipeline (zamiast per-krok DI) było konieczne do sensownych komunikatów wyjątków.
- **`ActiveDeviceValidationStep` jako połączony filtr+walidator** — KISS: zamiast dwóch oddzielnych kroków (FilterActive + ValidateNotAllBlocked) jeden krok z dostępem do oryginalnej listy. Trzy osobne kroki byłyby over-engineeringiem.

### Gotchas

- **Mapowanie SOAP → DTO w serwisie** — pierwotnie inline mapowanie `GetUserCapabilitiesResponse → CapabilitiesResponse` wylądowało w serwisie. Code review wymogło wyciągnięcie do `CapabilitiesMapper`. Zasada: serwis orkiestruje, mapper mapuje.
- **null-ifo zamiast Optional** — `if (x == null)` zastąpione przez `Optional.ofNullable(x).map(...).ifPresent(...)` w mapperach. Szczególnie dla `activationDate` (nullable) i `accountStatus` (null = brak konta SOAP).
- **Pipeline nie uruchamia się dla pustej listy urządzeń** — jeśli SOAP zwróci pustą listę (null accountStatus), wracamy wcześniej z pustą `CapabilitiesResponse` zanim pipeline zostanie wywołany. `ActiveDeviceValidationStep` obsługuje pustą listę jako `NoDevicesFoundException` dopiero gdy wejdzie do pipeline.
- **`toXmlDateTime`** — metoda była duplikowana między `MobilePushEndpoint` a mapperem. Przeniesiona do `MobilePushMapper` jako metoda prywatna (DRY).
- **`@Value @Builder` na `DeviceInfo`** — Lombok generuje `isMainDevice()` i `isPassiveMode()` (boolean getter z `is`-prefix) co zgadza się z tym czego oczekuje mapper. Uwaga: `d.isIsMainDevice()` — podwójne `is` bo Lombok zachowuje prefiks pola.
