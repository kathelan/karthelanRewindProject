# PostgreSQL Index Performance Analysis — auth_process table

## Setup

- Table: `auth_process`, **6 000 000 rows**, 100 000 distinct users (60 rows/user)
- 6 statuses evenly distributed: PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED, CLOSED (~10 rows/user/status)
- Engine: PostgreSQL 16 (TestContainers), Java 21, HikariCP
- Measurements: wall-clock ms + `EXPLAIN (ANALYZE, BUFFERS)` block counts

---

## Test 1 — UPDATE with index vs without index (user_id)

Single-row UPDATE per user: `WHERE user_id=? AND status='PENDING'`

| Scenario | Time/UPDATE | Blocks read | Speedup |
|---|---|---|---|
| No index | ~350 ms | ~55 000 | — |
| `INDEX ON user_id` | ~0.4 ms | ~64 | **853x time, 860x blocks** |

**Conclusion:** Without an index on a 6M-row table, every UPDATE triggers a full sequential scan
(~55 000 blocks × 8 KB ≈ 430 MB read per query). Adding a simple `user_id` index cuts this
to 64 blocks and makes the query ~850x faster.

---

## Test 2 — Scheduler bulk UPDATE: composite index vs none

Bulk UPDATE: `WHERE status='PENDING' AND expires_at < NOW()` — 600 000 rows (10% of table)

| Scenario | Time | Blocks read | Speedup |
|---|---|---|---|
| No composite index | ~8 000 ms | ~55 000 | — |
| `INDEX ON (status, expires_at)` | ~4 200 ms | ~580 | **95x blocks, ~1.9x time** |

**Why time speedup is smaller than block speedup:**
At 600k rows, write cost dominates. Reading 95x fewer blocks saves I/O on the SELECT side,
but each of the 600k rows still needs a WAL write + heap page update. The write throughput
becomes the bottleneck, not the scan.

**Key rule:** Index helps the WHERE clause (find rows). It cannot speed up the write of the
matched rows. At bulk scale (>10% of table), benefit of index on UPDATE shrinks.

---

## Test 3 — Single-row UPDATE: user_id index vs composite vs subquery

Each UPDATE touches exactly **1 row** (filtered by unique `delivery_id`), but the WHERE clause
includes `user_id` and `status`. Tested at N ∈ {100, 1000, 10 000, 100 000}.

### Results (EXPLAIN ANALYZE — blocks)

| Index strategy | Scan type | Blocks read per query |
|---|---|---|
| `INDEX ON user_id` | Bitmap Heap Scan | 63 (60 heap + 3 index) |
| `INDEX ON (user_id, status)` | Index Scan | 23 (20 heap + 3 index) |
| composite + subquery `WHERE id IN (...)` | Index Scan | 23 (identical to B) |

Block difference: **2.74x fewer reads** with composite index — consistent across all N.

### Results (wall-clock time)

| N | A: user_id idx | B: composite | C: subquery | B vs A time |
|---|---|---|---|---|
| 100 | — | — | — | ~1.0x (noise) |
| 1 000 | 322 ms | 281 ms | 291 ms | 1.15x |
| 10 000 | 2 933 ms | 3 041 ms | 2 731 ms | ~1.0x (noise) |
| 100 000 | 28 516 ms | 25 929 ms | 28 630 ms | 1.10x |

Time scales **linearly with N** (~0.29 ms/update constant), confirming:
- Bottleneck is RTT to DB + WAL write (fixed per row), not the index lookup
- Time difference between strategies A/B/C is measurement noise at this scale

**Why composite wins on blocks but not on time (local Docker):**
Local Docker RTT ≈ 0.1–0.2 ms dominates. On a real DB with disk I/O latency (e.g. EBS ~1 ms/IO),
2.74x fewer block reads = ~40 ms saved per query → composite becomes significantly faster.

### Subquery (C) — not worth it

`WHERE id IN (SELECT id FROM auth_process WHERE ...)` produces the same execution plan
as direct WHERE. Zero benefit, more complexity. Avoid.

---

## When does UPDATE performance degrade at scale?

### Index lookup cost — O(log N), nearly constant

PostgreSQL B-tree branching factor ≈ 300:

```
N =         6 000 000  →  3 B-tree levels  =  3 page reads
N =       600 000 000  →  4 B-tree levels  =  4 page reads
N = 60 000 000 000     →  5 B-tree levels  =  5 page reads
```

To double the lookup cost (3 → 6 levels) you'd need **729 billion rows**. Index depth is not
the problem.

### Write cost — constant regardless of N

Per UPDATE, PostgreSQL always writes:

```
WAL:             ~2 page writes  (old + new tuple version)
heap page:        1 page write
INDEX user_id:    1 page write
INDEX composite:  1 page write
INDEX PK (UUID):  1 page write
────────────────────────────────
total:           ~6 page writes  — does not grow with table size
```

### Real performance cliff: MVCC bloat

Every UPDATE leaves a **dead tuple** (old row version). autovacuum triggers at:

```
dead_tuples > 50 + 0.2 × live_rows

For 6M rows: trigger = 1 200 050 dead tuples
```

Days until autovacuum trigger (default config):

```
10 000 updates/day  →  120 days
100 000 updates/day →   12 days
500 000 updates/day →    2.4 days
```

If autovacuum cannot keep up (long transactions, disk pressure, few workers),
table **physically grows on disk** despite constant row count → cache hit ratio drops
→ UPDATE time spikes.

### Real performance cliff: shared_buffers exhaustion

When the table exceeds available RAM + `shared_buffers`, pages are cold on cache miss:

| Cache state | I/O per UPDATE | Time on SSD | Time on EBS (1ms/IO) |
|---|---|---|---|
| Hot cache (local) | 0 disk reads | ~0.3 ms | ~0.3 ms |
| Cold cache, composite idx | 23 disk reads | ~1–3 ms | ~23 ms |
| Cold cache, user_id idx | 63 disk reads | ~3–8 ms | ~63 ms |

**Alert threshold:** Monitor `pg_stat_user_tables.n_dead_tup / n_live_tup`.
When it exceeds 20–30%, autovacuum is falling behind.

---

## Summary — index selection guide

| Query pattern | Recommended index |
|---|---|
| `WHERE user_id = ?` only | `INDEX ON (user_id)` |
| `WHERE user_id = ? AND status = ?` | `INDEX ON (user_id, status)` — 2.74x fewer blocks |
| Scheduler: `WHERE status = ? AND expires_at < ?` | `INDEX ON (status, expires_at)` — 95x fewer blocks |
| Subquery `WHERE id IN (SELECT id WHERE ...)` | No benefit over direct WHERE — avoid |

**Bottom line:** Composite index pays off in I/O cost always. The time benefit becomes visible
under real disk latency or concurrent load — on local Docker it is masked by RTT noise.
