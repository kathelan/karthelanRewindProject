package pl.kathelan.dbperf;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.kathelan.dbperf.entity.AuthProcessStatus;
import pl.kathelan.dbperf.repository.AuthProcessRepository;
import pl.kathelan.dbperf.service.AuthProcessUpdateService;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Integration performance test — 9 ordered phases:
 *
 * <pre>
 * Phase 1 — @Modifying UPDATE,  NO index          (baseline: Seq Scan)
 * Phase 2 — save() INSERT,      NO index          (baseline)
 * Phase 3 — CREATE INDEX (user_id, status)
 * Phase 4 — @Modifying UPDATE,  WITH index        (~43x speedup: Bitmap Index Scan)
 * Phase 5 — save() INSERT,      WITH index        (index write overhead on INSERT)
 * Phase 6 — Transaction overhead: 1000 tx vs 1 tx (BEGIN/COMMIT cost)
 * Phase 7 — Partial index vs full composite index (size + speed)
 * Phase 8  — Batch insert: saveAll() vs 1000×save() (JDBC batch)
 * Phase 9  — N+1 problem: 100×findById vs findAllById (SQL statement count)
 * Phase 10 — Slow query log: Hibernate SQL_SLOW + PostgreSQL log_min_duration_statement
 * </pre>
 *
 * <p>Requires docker-compose postgres on port 5433:
 * <pre>docker-compose -f db-performance/docker-compose.yml up -d</pre>
 *
 * <p>Run:
 * <pre>mvn test -pl db-performance -Dgroups=performance -Dexcluded.test.groups=""</pre>
 */
@Tag("performance")
@SpringBootTest(classes = DbPerfTestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthProcessJpaPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(AuthProcessJpaPerformanceTest.class);

    private static final int SEED_COUNT = 600_000;
    private static final int N          = 1_000;

    /*
     * User ranges for @Modifying phases — each needs intact PENDING rows:
     *   Phase 1 (@Modifying no idx)       : users 0    .. 999
     *   Phase 4 (@Modifying with idx)     : users 1000 .. 1999
     *   Phase 6a (individual transactions): users 2000 .. 2999
     *   Phase 6b (one transaction)        : users 3000 .. 3999
     *   Phase 7  (partial index)          : users 4000 .. 4999
     *   Phases 2, 5, 8, 9 use inserts/reads — no range constraint
     */

    @Autowired private AuthProcessUpdateService service;
    @Autowired private AuthProcessRepository    repository;
    @Autowired private JdbcTemplate             jdbcTemplate;
    @Autowired private EntityManagerFactory     emf;

    // Results stored for final summary
    private long modifyingNoIdxMs;
    private long insertNoIdxMs;
    private long modifyingWithIdxMs;
    private long insertWithIdxMs;
    private long txIndividualMs;
    private long txOneMs;
    private long partialIdxMs;
    private long batchIndividualMs;
    private long batchSaveAllMs;
    private long nPlusOneMs;
    private long findAllByIdMs;

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeAll
    void seed() {
        log.info("=== Seeding {} rows ===", SEED_COUNT);
        long t = System.currentTimeMillis();
        jdbcTemplate.execute("""
                INSERT INTO auth_process (id, user_id, auth_method, delivery_id, status, created_at, updated_at, expires_at)
                SELECT gen_random_uuid(),
                       'user_' || (s % 10000),
                       'BIOMETRIC',
                       'delivery_' || s,
                       CASE (s % 6)
                           WHEN 0 THEN 'PENDING'
                           WHEN 1 THEN 'APPROVED'
                           WHEN 2 THEN 'REJECTED'
                           WHEN 3 THEN 'EXPIRED'
                           WHEN 4 THEN 'CANCELLED'
                           ELSE        'CLOSED'
                       END,
                       NOW(), NOW(), NOW() + interval '5 minutes'
                FROM generate_series(1, 600000) AS s
                """);
        log.info("Seeded {} rows in {} ms", SEED_COUNT, System.currentTimeMillis() - t);
    }

    @AfterAll
    void summary() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_jpa_user_status");
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_partial_pending");

        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║                   FULL SUITE SUMMARY (N={})                    ║", N);
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  [1/4] @Modifying NO  idx: %,7d ms  (%5.2f ms/op)            ║", modifyingNoIdxMs,  avg(modifyingNoIdxMs)));
        log.info("{}", String.format("║  [4/4] @Modifying WITH idx:%,7d ms  (%5.2f ms/op)  %4.0fx  ║", modifyingWithIdxMs, avg(modifyingWithIdxMs), ratio(modifyingNoIdxMs, modifyingWithIdxMs)));
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  [2/5] INSERT NO  idx:     %,7d ms  (%5.2f ms/op)            ║", insertNoIdxMs,  avg(insertNoIdxMs)));
        log.info("{}", String.format("║  [5/5] INSERT WITH idx:    %,7d ms  (%5.2f ms/op)            ║", insertWithIdxMs, avg(insertWithIdxMs)));
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  [6a]  1000 individual tx: %,7d ms  (%5.2f ms/op)            ║", txIndividualMs, avg(txIndividualMs)));
        log.info("{}", String.format("║  [6b]  1 tx (N=1000):      %,7d ms  (%5.2f ms/op)  %4.0fx  ║", txOneMs, avg(txOneMs), ratio(txIndividualMs, txOneMs)));
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  [4]   Full composite idx: %,7d ms  (%5.2f ms/op)            ║", modifyingWithIdxMs, avg(modifyingWithIdxMs)));
        log.info("{}", String.format("║  [7]   Partial idx:        %,7d ms  (%5.2f ms/op)            ║", partialIdxMs, avg(partialIdxMs)));
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  [8a]  1000 × save():      %,7d ms  (%5.2f ms/op)            ║", batchIndividualMs, avg(batchIndividualMs)));
        log.info("{}", String.format("║  [8b]  saveAll(1000):      %,7d ms  total          %4.0fx  ║", batchSaveAllMs, ratio(batchIndividualMs, batchSaveAllMs)));
        log.info("╠══════════════════════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  [9a]  N+1 (100×findById): %,7d ms                           ║", nPlusOneMs));
        log.info("{}", String.format("║  [9b]  findAllById(100):   %,7d ms               %4.0fx  ║", findAllByIdMs, ratio(nPlusOneMs, findAllByIdMs)));
        log.info("╚══════════════════════════════════════════════════════════════════╝");
    }

    // ── Phase 1: @Modifying WITHOUT index ────────────────────────────────────

    @Test @Order(1)
    void phase1_modifying_noIndex() {
        log.info("=== Phase 1: @Modifying UPDATE — NO INDEX ===");
        explainUpdate("NO INDEX", "user_0");
        modifyingNoIdxMs = measureModifying(0);
        logPhase("@Modifying | NO INDEX", modifyingNoIdxMs);
    }

    // ── Phase 2: INSERT WITHOUT index ────────────────────────────────────────

    @Test @Order(2)
    void phase2_insert_noIndex() {
        log.info("=== Phase 2: save() INSERT — NO INDEX ===");
        insertNoIdxMs = measureIndividualInserts();
        logPhase("save() INSERT | NO INDEX", insertNoIdxMs);
    }

    // ── Phase 3: create composite index ──────────────────────────────────────

    @Test @Order(3)
    void phase3_createCompositeIndex() {
        log.info("=== Phase 3: CREATE INDEX (user_id, status) ===");
        jdbcTemplate.execute("CREATE INDEX idx_jpa_user_status ON auth_process(user_id, status)");
        jdbcTemplate.execute("ANALYZE auth_process");
        log.info("Index created. Size: {}", indexSize("idx_jpa_user_status"));
    }

    // ── Phase 4: @Modifying WITH composite index ──────────────────────────────

    @Test @Order(4)
    void phase4_modifying_withCompositeIndex() {
        log.info("=== Phase 4: @Modifying UPDATE — WITH INDEX (user_id, status) ===");
        explainUpdate("WITH COMPOSITE INDEX", "user_1000");
        modifyingWithIdxMs = measureModifying(N);
        logPhase("@Modifying | WITH COMPOSITE INDEX", modifyingWithIdxMs);
    }

    // ── Phase 5: INSERT WITH composite index ─────────────────────────────────

    @Test @Order(5)
    void phase5_insert_withCompositeIndex() {
        log.info("=== Phase 5: save() INSERT — WITH INDEX ===");
        insertWithIdxMs = measureIndividualInserts();
        logPhase("save() INSERT | WITH COMPOSITE INDEX", insertWithIdxMs);
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_jpa_user_status");
    }

    // ── Phase 6: transaction overhead ────────────────────────────────────────

    @Test @Order(6)
    void phase6_transactionOverhead() {
        log.info("=== Phase 6: Transaction overhead — 1000 individual tx vs 1 tx ===");

        jdbcTemplate.execute("CREATE INDEX idx_jpa_user_status ON auth_process(user_id, status)");
        jdbcTemplate.execute("ANALYZE auth_process");

        // A: 1000 individual @Transactional calls → 1000 × (BEGIN + UPDATE + COMMIT)
        log.info("--- A: 1000 calls, each with its own @Transactional ---");
        txIndividualMs = measureModifying(2 * N);
        log.info("  {} individual tx: {} ms  ({} ms/op)",
                N, txIndividualMs, String.format("%.2f", avg(txIndividualMs)));

        // B: 1 outer @Transactional wrapping all 1000 updates → 1 BEGIN + 1000×UPDATE + 1 COMMIT
        log.info("--- B: all {} updates inside ONE @Transactional ---", N);
        List<String> userIds = IntStream.range(3 * N, 4 * N)
                .mapToObj(i -> "user_" + i)
                .toList();
        long t = System.currentTimeMillis();
        service.updateAllInOneTransaction(userIds, AuthProcessStatus.PENDING, AuthProcessStatus.APPROVED);
        txOneMs = System.currentTimeMillis() - t;
        log.info("  1 tx (N={}): {} ms  ({} ms/op)", N, txOneMs, String.format("%.2f", avg(txOneMs)));

        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_jpa_user_status");

        log.info("╔══════════════════════════════════════════════════╗");
        log.info("{}", String.format("║  1000 individual tx: %,6d ms  (%5.2f ms/op)  ║", txIndividualMs, avg(txIndividualMs)));
        log.info("{}", String.format("║  1 transaction:      %,6d ms  (%5.2f ms/op)  ║", txOneMs,        avg(txOneMs)));
        log.info("{}", String.format("║  Speedup:                     %10.1fx          ║", ratio(txIndividualMs, txOneMs)));
        log.info("╚══════════════════════════════════════════════════╝");
    }

    // ── Phase 7: partial index ────────────────────────────────────────────────

    @Test @Order(7)
    void phase7_partialIndex() {
        log.info("=== Phase 7: Partial index — (user_id) WHERE status='PENDING' ===");

        // Partial index — only indexes the ~100k PENDING rows, ignores the other 500k
        jdbcTemplate.execute(
                "CREATE INDEX idx_partial_pending ON auth_process(user_id) WHERE status = 'PENDING'");
        jdbcTemplate.execute("ANALYZE auth_process");

        explainUpdate("PARTIAL INDEX WHERE status='PENDING'", "user_" + (4 * N));
        partialIdxMs = measureModifying(4 * N);
        logPhase("@Modifying | PARTIAL INDEX (user_id) WHERE status='PENDING'", partialIdxMs);

        // Size comparison requires recreating the full index briefly
        jdbcTemplate.execute("CREATE INDEX idx_jpa_user_status ON auth_process(user_id, status)");
        log.info("┌─ Index size comparison ─────────────────────────────────────");
        log.info("│  Full composite (user_id, status):        {}", indexSize("idx_jpa_user_status"));
        log.info("│  Partial        (user_id) WHERE PENDING:  {}", indexSize("idx_partial_pending"));
        log.info("└────────────────────────────────────────────────────────────");
        log.info("{}", String.format("  Full  index time: %.2f ms/op  (from phase 4)", avg(modifyingWithIdxMs)));
        log.info("{}", String.format("  Partial idx time: %.2f ms/op", avg(partialIdxMs)));

        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_jpa_user_status");
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_partial_pending");
    }

    // ── Phase 8: batch insert ─────────────────────────────────────────────────

    @Test @Order(8)
    void phase8_batchInsert() {
        log.info("=== Phase 8: Batch insert — 1000×save() vs saveAll(1000) ===");

        // A: 1000 individual save() — 1000 JDBC round-trips, 1000 transactions
        log.info("--- A: 1000 individual save() calls ---");
        batchIndividualMs = measureIndividualInserts();
        log.info("  1000 × save(): {} ms  ({} ms/op)", batchIndividualMs, String.format("%.2f", avg(batchIndividualMs)));

        // B: saveAll(list) — Hibernate batches 500 INSERTs per JDBC call → ~2 round-trips, 1 tx
        log.info("--- B: saveAll(list of {}) — JDBC batch_size=500 ---", N);
        long t = System.currentTimeMillis();
        service.insertBatchBySaveAll(N);
        batchSaveAllMs = System.currentTimeMillis() - t;
        log.info("  saveAll({}): {} ms total", N, batchSaveAllMs);

        log.info("╔══════════════════════════════════════════════════╗");
        log.info("{}", String.format("║  1000 × save():   %,6d ms  (%5.2f ms/op)   ║", batchIndividualMs, avg(batchIndividualMs)));
        log.info("{}", String.format("║  saveAll(1000):   %,6d ms  total           ║", batchSaveAllMs));
        log.info("{}", String.format("║  Speedup:                  %10.1fx          ║", ratio(batchIndividualMs, batchSaveAllMs)));
        log.info("╚══════════════════════════════════════════════════╝");
    }

    // ── Phase 9: N+1 problem ──────────────────────────────────────────────────

    @Test @Order(9)
    void phase9_nPlusOne() {
        log.info("=== Phase 9: N+1 — 100×findById vs findAllById ===");

        List<UUID> ids = jdbcTemplate.query(
                "SELECT id FROM auth_process LIMIT 100",
                (rs, i) -> UUID.fromString(rs.getString("id")));

        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

        // Pattern A: N+1 — every findById opens its own EntityManager → its own SELECT
        log.info("--- Pattern A: 100 × repository.findById(id) ---");
        stats.clear();
        long t = System.currentTimeMillis();
        for (UUID id : ids) {
            repository.findById(id);
        }
        nPlusOneMs = System.currentTimeMillis() - t;
        long stmtsA = stats.getPrepareStatementCount();
        log.info("  100 × findById:   {} ms  |  SQL statements: {}", nPlusOneMs, stmtsA);

        // Pattern B: 1 query — SELECT ... WHERE id IN (id1, id2, ..., id100)
        log.info("--- Pattern B: repository.findAllById(list of 100) ---");
        stats.clear();
        t = System.currentTimeMillis();
        repository.findAllById(ids);
        findAllByIdMs = System.currentTimeMillis() - t;
        long stmtsB = stats.getPrepareStatementCount();
        log.info("  findAllById(100): {} ms  |  SQL statements: {}", findAllByIdMs, stmtsB);

        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("{}", String.format("║  N+1: 100 × findById:  %,5d ms  | %3d SQL statements   ║", nPlusOneMs,   stmtsA));
        log.info("{}", String.format("║  findAllById(100):     %,5d ms  | %3d SQL statement    ║", findAllByIdMs, stmtsB));
        log.info("{}", String.format("║  Speedup:              %22.1fx                  ║", ratio(nPlusOneMs, findAllByIdMs)));
        log.info("╚══════════════════════════════════════════════════════════╝");
    }

    // ── Phase 10: slow query log ──────────────────────────────────────────────

    /**
     * Demonstrates two independent slow-query logging channels:
     *
     * <ul>
     *   <li><b>Hibernate SQL_SLOW</b> (logger {@code org.hibernate.SQL_SLOW} at WARN) —
     *       fires only for SELECT queries tracked by Hibernate's session event listener.
     *       JPQL {@code @Modifying} UPDATEs bypass that listener.
     *       Threshold: {@code hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS} (application.properties).<br>
     *       <em>Note on local Docker:</em> Hibernate measures the raw JDBC execution time,
     *       which excludes JPA/transaction overhead. On a hot shared_buffer cache the DB returns
     *       results in &lt;5 ms even without an index, so the WARN fires only on cold I/O
     *       (production EBS/SSD). The wall-clock time logged below includes Spring/JPA overhead
     *       and is always higher than Hibernate's internal measurement.</li>
     *   <li><b>PostgreSQL {@code log_min_duration_statement}</b> — fires for every SQL
     *       statement (SELECT and UPDATE) once server-side execution time exceeds the threshold;
     *       visible via {@code docker logs postgres-perf | grep 'duration:'}.</li>
     * </ul>
     *
     * <p>Both thresholds are set to 20 ms.
     */
    @Test @Order(10)
    void phase10_slowQueryLog() {
        log.info("=== Phase 10: Slow query log — Hibernate SQL_SLOW + PostgreSQL log ===");

        // Ensure no index exists so every query forces a full Seq Scan (~35–50 ms each)
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_jpa_user_status");
        jdbcTemplate.execute("DROP INDEX IF EXISTS idx_partial_pending");

        // Enable PostgreSQL slow query log for statements > 20 ms (no container restart needed)
        jdbcTemplate.execute("ALTER SYSTEM SET log_min_duration_statement = 20");
        jdbcTemplate.execute("SELECT pg_reload_conf()");
        log.info("PostgreSQL: log_min_duration_statement = 20 ms — active");

        // Hibernate: LOG_QUERIES_SLOWER_THAN_MS=20 is set in application.properties.
        // It tracks SELECT queries via session event listener → logs to org.hibernate.SQL_SLOW (WARN).
        // Note: @Modifying JPQL UPDATEs bypass the session event listener — no SQL_SLOW for them.
        log.info("Hibernate:  LOG_QUERIES_SLOWER_THAN_MS = 20 ms — active (application.properties)");
        log.info("           (tracks SELECT queries only — not @Modifying UPDATEs)");
        log.info("");

        // ── Part A: SELECT without index → triggers Hibernate SQL_SLOW (WARN lines below) ──
        log.info("--- Part A: SELECT without index → Hibernate SQL_SLOW fires ---");
        for (int i = 5000; i < 5005; i++) {
            long t = System.currentTimeMillis();
            repository.findFirstByUserIdAndStatus("user_" + i, AuthProcessStatus.PENDING);
            long ms = System.currentTimeMillis() - t;
            log.info("  SELECT user_{}: {} ms{}", i, ms, ms > 20 ? "  ← Hibernate SQL_SLOW triggered" : "");
        }
        log.info("  ↑ Look for [WARN] o.h.SQL_SLOW entries interleaved above");

        log.info("");

        // ── Part B: UPDATE without index → triggers PostgreSQL log only ─────────────────
        log.info("--- Part B: UPDATE without index → PostgreSQL log fires ---");
        for (int i = 5000; i < 5005; i++) {
            long t = System.currentTimeMillis();
            int updated = service.updateByModifying("user_" + i, AuthProcessStatus.PENDING, AuthProcessStatus.APPROVED);
            long ms = System.currentTimeMillis() - t;
            log.info("  UPDATE user_{}: {} row(s) in {} ms{}", i, updated, ms, ms > 20 ? "  ← PostgreSQL log triggered" : "");
        }

        log.info("");
        log.info("┌─ Where to find the slow query entries ──────────────────────────────");
        log.info("│  Hibernate SQL_SLOW → [WARN] o.h.SQL_SLOW lines in this test output (Part A)");
        log.info("│  PostgreSQL log     → docker logs postgres-perf | grep 'duration:'");
        log.info("│  PG format: duration: <N> ms  statement: SELECT/UPDATE ...");
        log.info("└────────────────────────────────────────────────────────────────────");

        // Reset PostgreSQL slow query threshold
        jdbcTemplate.execute("ALTER SYSTEM RESET log_min_duration_statement");
        jdbcTemplate.execute("SELECT pg_reload_conf()");
        log.info("PostgreSQL: log_min_duration_statement reset to default");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long measureModifying(int userOffset) {
        long t = System.currentTimeMillis();
        for (int i = userOffset; i < userOffset + N; i++) {
            service.updateByModifying("user_" + i, AuthProcessStatus.PENDING, AuthProcessStatus.APPROVED);
        }
        long ms = System.currentTimeMillis() - t;
        log.info("  @Modifying {} calls: {} ms  ({} ms/call)", N, ms, String.format("%.2f", avg(ms)));
        return ms;
    }

    private long measureIndividualInserts() {
        long t = System.currentTimeMillis();
        for (int i = 0; i < N; i++) {
            service.insertBySave("user_insert_" + i, AuthProcessStatus.PENDING);
        }
        long ms = System.currentTimeMillis() - t;
        log.info("  {} × save(): {} ms  ({} ms/call)", N, ms, String.format("%.2f", avg(ms)));
        return ms;
    }

    private void explainUpdate(String label, String userId) {
        List<String> plan = jdbcTemplate.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS)
                UPDATE auth_process SET status='APPROVED', updated_at=NOW()
                WHERE  user_id = ? AND status = 'PENDING'
                """, String.class, userId);

        int blocks = plan.stream()
                .filter(l -> l.contains("Buffers:"))
                .findFirst()
                .map(l -> parseBlocks(l, "shared hit=(\\d+)") + parseBlocks(l, "\\bread=(\\d+)"))
                .orElse(0);

        log.info("┌─ EXPLAIN [{}] — user='{}' ────────────────────────────", label, userId);
        plan.forEach(l -> log.info("│  {}", l));
        log.info("│  → total blocks: {}", blocks);
        log.info("└────────────────────────────────────────────────────────────");
    }

    private String indexSize(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT pg_size_pretty(pg_relation_size(?))", String.class, name);
    }

    private void logPhase(String label, long ms) {
        log.info("┌─ {} ───────────────────────────────────────", label);
        log.info("│  N={} | total={} ms | avg={} ms/op", N, ms, String.format("%.2f", avg(ms)));
        log.info("└────────────────────────────────────────────────────────────");
    }

    private double avg(long ms)                       { return (double) ms / N; }
    private double ratio(long bigger, long smaller)   { return smaller > 0 ? (double) bigger / smaller : 0; }

    private int parseBlocks(String line, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
