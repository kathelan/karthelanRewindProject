# pollAndUpdatePushStatuses — Performance Refactor

## Problem

`pollAndUpdatePushStatuses` iterated over all PENDING processes **sequentially** — each SOAP call blocked the next one.
The scheduler fires every 3 seconds. With enough PENDING processes, a single poll execution exceeded that interval,
causing scheduler runs to pile up.

```java
// before
repository.findAllPending().forEach(process -> {
    resilientCaller.call(() -> mobilePushClient.getPushStatus(process.deliveryId()));
    ...
});
```

## Fix

One-line change — `forEach` → `parallelStream().forEach()`.

```java
// after
repository.findAllPending().parallelStream().forEach(process -> {
    resilientCaller.call(() -> mobilePushClient.getPushStatus(process.deliveryId()));
    ...
});
```

## Benchmark results (50ms simulated SOAP latency per call)

| N pending processes | Sequential (before) | Parallel (after) | Speedup |
|---------------------|---------------------|------------------|---------|
| 10                  | ~536ms              | ~111ms           | 4x      |
| 50                  | ~2694ms             | ~376ms           | 6x      |
| 100                 | ~5395ms             | ~759ms           | 7x      |

Measured by `PollPerformanceTest` (`@Tag("performance")`).
Run manually: `mvn test "-Dexcluded.test.groups=" -Dgroups=performance -pl auth-service/auth-service-rest`

## Known trade-offs

- `parallelStream` uses the common ForkJoinPool (shared JVM-wide). Under heavy load this can starve other tasks.
  If that becomes a problem, replace with a dedicated `ExecutorService`.
- `InMemoryAuthProcessRepository` is not thread-safe by contract — concurrent writes from parallel poll
  are safe only because each process is an independent record with its own UUID key.
  At the JPA layer: optimistic locking + unique constraint will be required.
- Circuit breaker in `ResilientCaller` has shared state — concurrent calls are fine (it is thread-safe),
  but a burst of N failures will trip the breaker faster than N sequential failures would.