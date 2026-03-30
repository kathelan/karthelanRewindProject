package pl.kathelan.dbperf;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
@Testcontainers
class AuthProcessUpdatePerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(AuthProcessUpdatePerformanceTest.class);

    private static final int TOTAL_ROWS     = 6_000_000;
    private static final int DISTINCT_USERS = 100_000;
    private static final int N_NO_INDEX     = 20;
    private static final int N_WITH_INDEX   = 500;

    // user_60000: s=60000 → 60000%6=0 → PENDING, outside range of other tests
    private static final String EXPLAIN_USER = "user_60000";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("perf_test")
            .withUsername("test")
            .withPassword("test");

    static JdbcTemplate jdbc;

    @BeforeAll
    static void setup() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setMaximumPoolSize(4);
        jdbc = new JdbcTemplate(new HikariDataSource(cfg));

        createSchema();
        seedData();
    }

    @Test
    void updatePerformance_withIndexVsWithout() {
        // ── Phase 1: no index ────────────────────────────────────────────────
        log.info("=== Phase 1: UPDATE without index (N={}) ===", N_NO_INDEX);
        long noIndexMs     = measureUpdates(userIds(0, N_NO_INDEX));
        int  noIndexBlocks = explainAndCountBlocks("WITHOUT INDEX");

        // ── Add index ────────────────────────────────────────────────────────
        log.info("=== Creating index on user_id ===");
        jdbc.execute("CREATE INDEX idx_auth_process_user_id ON auth_process(user_id)");
        jdbc.execute("ANALYZE auth_process");

        // ── Phase 2: with index ──────────────────────────────────────────────
        log.info("=== Phase 2: UPDATE with index (N={}) ===", N_WITH_INDEX);
        long withIndexMs     = measureUpdates(userIds(N_NO_INDEX, N_NO_INDEX + N_WITH_INDEX));
        int  withIndexBlocks = explainAndCountBlocks("WITH INDEX");

        // ── Results ──────────────────────────────────────────────────────────
        double noIndexAvg    = (double) noIndexMs   / N_NO_INDEX;
        double withIndexAvg  = (double) withIndexMs / N_WITH_INDEX;
        double timeSpeedup   = noIndexAvg / withIndexAvg;
        double blockSpeedup  = (double) noIndexBlocks / withIndexBlocks;

        log.info("╔═══════════════════════════════════════════════════╗");
        log.info("║              PERFORMANCE RESULTS                  ║");
        log.info("╠═══════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  Table rows:        %,12d                ║", TOTAL_ROWS));
        log.info("╠═══════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  Time  (no index):  %10.1f ms / UPDATE      ║", noIndexAvg));
        log.info("{}", String.format("║  Time  (w/ index):  %10.1f ms / UPDATE      ║", withIndexAvg));
        log.info("{}", String.format("║  Time  speedup:     %10.1fx                  ║", timeSpeedup));
        log.info("╠═══════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  Blocks (no index): %,12d  (~%s)      ║", noIndexBlocks,   toMb(noIndexBlocks)));
        log.info("{}", String.format("║  Blocks (w/ index): %,12d  (~%s)       ║", withIndexBlocks, toKb(withIndexBlocks)));
        log.info("{}", String.format("║  Block  speedup:    %10.1fx fewer reads      ║", blockSpeedup));
        log.info("╚═══════════════════════════════════════════════════╝");

        assertThat(withIndexAvg)
                .as("UPDATE with index should be at least 5x faster than without")
                .isLessThan(noIndexAvg / 5.0);
    }

    @Test
    void schedulerQuery_compositeIndexVsNone() {
        // Seed: mark 10k users (600k rows = 10% of table) as expired
        jdbc.update("""
                UPDATE auth_process SET expires_at = NOW() - interval '1 hour'
                WHERE user_id IN (
                    SELECT DISTINCT user_id FROM auth_process
                    WHERE  status = 'PENDING'
                    LIMIT  10000
                )
                """);

        // ── Phase 1: no composite index ──────────────────────────────────────
        log.info("=== Scheduler: EXPIRE without composite index ===");
        int  noIndexBlocks = explainExpiredAndCountBlocks("WITHOUT COMPOSITE INDEX");
        long noIndexMs     = measureExpiredUpdate();

        // Reset expired rows back to PENDING for phase 2
        jdbc.update("UPDATE auth_process SET status = 'PENDING' WHERE status = 'EXPIRED'");
        // Reclaim dead tuples (MVCC leaves old row versions until VACUUM)
        log.info("=== VACUUM (reclaiming dead tuples after bulk update) ===");
        jdbc.execute("VACUUM auth_process");

        // ── Create composite index ────────────────────────────────────────────
        log.info("=== Creating composite index on (status, expires_at) ===");
        jdbc.execute("CREATE INDEX idx_status_expires ON auth_process(status, expires_at)");
        jdbc.execute("ANALYZE auth_process");

        // ── Phase 2: with composite index ────────────────────────────────────
        log.info("=== Scheduler: EXPIRE with composite index (status, expires_at) ===");
        int  withIndexBlocks = explainExpiredAndCountBlocks("WITH COMPOSITE INDEX");
        long withIndexMs     = measureExpiredUpdate();

        // ── Results ──────────────────────────────────────────────────────────
        double blockSpeedup = (double) noIndexBlocks / withIndexBlocks;
        double timeSpeedup  = (double) noIndexMs / withIndexMs;

        log.info("╔═══════════════════════════════════════════════════╗");
        log.info("║         SCHEDULER QUERY RESULTS                   ║");
        log.info("╠═══════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  Expired rows updated:  %,12d (10%% of table) ║", 10_000 * 60));
        log.info("╠═══════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  Time  (no index):  %10d ms             ║", noIndexMs));
        log.info("{}", String.format("║  Time  (w/ index):  %10d ms             ║", withIndexMs));
        log.info("{}", String.format("║  Time  speedup:     %10.1fx                  ║", timeSpeedup));
        log.info("╠═══════════════════════════════════════════════════╣");
        log.info("{}", String.format("║  Blocks (no index): %,12d  (~%s)      ║", noIndexBlocks,   toMb(noIndexBlocks)));
        log.info("{}", String.format("║  Blocks (w/ index): %,12d  (~%s)      ║", withIndexBlocks, toMb(withIndexBlocks)));
        log.info("{}", String.format("║  Block  speedup:    %10.1fx fewer reads      ║", blockSpeedup));
        log.info("╚═══════════════════════════════════════════════════╝");

        // At bulk scale (60k rows), write cost dominates — assert on block reads, not time
        assertThat(withIndexBlocks)
                .as("Composite index should reduce block reads by at least 10x")
                .isLessThan(noIndexBlocks / 10);
    }

    @ParameterizedTest(name = "N={0} single-row updates")
    @ValueSource(ints = {100, 1_000, 10_000, 100_000})
    void singleRowUpdate_indexStrategiesComparison(int n) {
        // fetch N rows with VARIED statuses — delivery_id is unique → always 1 row per UPDATE
        List<String[]> targets = jdbc.query(
                "SELECT user_id, status, delivery_id FROM auth_process LIMIT ?",
                (rs, i) -> new String[]{rs.getString("user_id"), rs.getString("status"), rs.getString("delivery_id")},
                n);
        String explainUser   = targets.get(0)[0];
        String explainStatus = targets.get(0)[1];

        // ── Phase A: index on user_id ─────────────────────────────────────────
        log.info("=== [N={}] Phase A: INDEX ON user_id ===", n);
        jdbc.execute("CREATE INDEX idx_perf_uid ON auth_process(user_id)");
        jdbc.execute("ANALYZE auth_process");
        int  uidBlocks = explainAndCountBlocks("A:user_id idx", explainUser, explainStatus);
        long uidMs     = measureDirectWhere(targets);
        jdbc.execute("DROP INDEX idx_perf_uid");

        // ── Phase B: composite index (user_id, status) ────────────────────────
        log.info("=== [N={}] Phase B: INDEX ON (user_id, status) ===", n);
        jdbc.execute("CREATE INDEX idx_perf_uid_status ON auth_process(user_id, status)");
        jdbc.execute("ANALYZE auth_process");
        int  compBlocks = explainAndCountBlocks("B:composite idx", explainUser, explainStatus);
        long compMs     = measureDirectWhere(targets);
        jdbc.execute("DROP INDEX idx_perf_uid_status");

        // ── Phase C: composite index + subquery WHERE ─────────────────────────
        log.info("=== [N={}] Phase C: composite + WHERE id IN (subquery) ===", n);
        jdbc.execute("CREATE INDEX idx_perf_uid_status ON auth_process(user_id, status)");
        jdbc.execute("ANALYZE auth_process");
        long subMs = measureSubqueryWhere(targets);
        jdbc.execute("DROP INDEX idx_perf_uid_status");

        // ── Results ───────────────────────────────────────────────────────────
        double bVsA = uidMs  > 0 ? (double) uidMs  / compMs : 1.0;
        double cVsB = compMs > 0 ? (double) compMs / subMs  : 1.0;

        log.info("{}", String.format("╔═ N=%,7d ════════════════════════════════════════════════════╗", n));
        log.info("{}", String.format("║  A) user_id idx:          %,8d ms   %,8d blocks          ║", uidMs,    uidBlocks));
        log.info("{}", String.format("║  B) composite (uid,stat): %,8d ms   %,8d blocks          ║", compMs,   compBlocks));
        log.info("{}", String.format("║  C) composite + subquery: %,8d ms   (same index as B)    ║", subMs));
        log.info("{}", String.format("║  B vs A speedup:  %6.2fx time   %6.2fx blocks             ║", bVsA, (double) uidBlocks / compBlocks));
        log.info("{}", String.format("║  C vs B speedup:  %6.2fx time                             ║", cVsB));
        log.info("╚══════════════════════════════════════════════════════════════╝");

        // Composite index must read fewer blocks than user_id-only index (structural guarantee)
        assertThat(compBlocks)
                .as("Composite (user_id, status) index should read fewer blocks than user_id-only index")
                .isLessThan(uidBlocks);
    }

    @AfterEach
    void vacuumAfterEach() {
        jdbc.execute("DROP INDEX IF EXISTS idx_perf_uid");
        jdbc.execute("DROP INDEX IF EXISTS idx_perf_uid_status");
        jdbc.execute("VACUUM auth_process");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Direct WHERE: user_id=? AND status=? AND delivery_id=? — updates updated_at only, no state change */
    private long measureDirectWhere(List<String[]> targets) {
        long start = System.currentTimeMillis();
        for (String[] t : targets) {
            jdbc.update("""
                    UPDATE auth_process
                    SET    updated_at  = NOW()
                    WHERE  user_id     = ?
                    AND    status      = ?
                    AND    delivery_id = ?
                    """, t[0], t[1], t[2]);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("  {} direct-WHERE updates in {}ms (avg {} ms/update)",
                targets.size(), elapsed, String.format("%.2f", (double) elapsed / targets.size()));
        return elapsed;
    }

    /** Subquery WHERE: id IN (SELECT id ... WHERE user_id=? AND status=? AND delivery_id=?) */
    private long measureSubqueryWhere(List<String[]> targets) {
        long start = System.currentTimeMillis();
        for (String[] t : targets) {
            jdbc.update("""
                    UPDATE auth_process
                    SET    updated_at = NOW()
                    WHERE  id IN (
                        SELECT id FROM auth_process
                        WHERE  user_id     = ?
                        AND    status      = ?
                        AND    delivery_id = ?
                    )
                    """, t[0], t[1], t[2]);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("  {} subquery-WHERE updates in {}ms (avg {} ms/update)",
                targets.size(), elapsed, String.format("%.2f", (double) elapsed / targets.size()));
        return elapsed;
    }

    private long measureUpdates(List<String> userIds) {
        long start = System.currentTimeMillis();
        for (String userId : userIds) {
            jdbc.update("""
                    UPDATE auth_process
                    SET    status     = 'APPROVED',
                           updated_at = NOW()
                    WHERE  user_id = ?
                    AND    status  = 'PENDING'
                    """, userId);
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("  {} updates done in {}ms (avg {} ms/update)",
                userIds.size(), elapsed, String.format("%.1f", (double) elapsed / userIds.size()));
        return elapsed;
    }

    private long measureExpiredUpdate() {
        long start = System.currentTimeMillis();
        int updated = jdbc.update("""
                UPDATE auth_process
                SET    status     = 'EXPIRED',
                       updated_at = NOW()
                WHERE  status     = 'PENDING'
                AND    expires_at < NOW()
                """);
        long elapsed = System.currentTimeMillis() - start;
        log.info("  Expired {} rows in {}ms", updated, elapsed);
        return elapsed;
    }

    private int explainExpiredAndCountBlocks(String label) {
        List<String> plan = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT * FROM auth_process
                WHERE  status     = 'PENDING'
                AND    expires_at < NOW()
                """, String.class);
        log.info("EXPLAIN ANALYZE [{}]:\n{}", label, String.join("\n", plan));
        return plan.stream()
                .filter(line -> line.contains("Buffers:"))
                .findFirst()
                .map(line -> parseValue(line, "shared hit=(\\d+)") + parseValue(line, "\\bread=(\\d+)"))
                .orElse(0);
    }

    private int explainAndCountBlocks(String label, String userId, String status) {
        List<String> plan = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT * FROM auth_process
                WHERE  user_id = ?
                AND    status  = ?
                """, String.class, userId, status);
        log.info("EXPLAIN ANALYZE [{}]:\n{}", label, String.join("\n", plan));
        return plan.stream()
                .filter(line -> line.contains("Buffers:"))
                .findFirst()
                .map(line -> parseValue(line, "shared hit=(\\d+)") + parseValue(line, "\\bread=(\\d+)"))
                .orElse(0);
    }

    private int explainAndCountBlocks(String label) {
        List<String> plan = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT * FROM auth_process
                WHERE  user_id = ?
                AND    status  = 'PENDING'
                """, String.class, EXPLAIN_USER);
        log.info("EXPLAIN ANALYZE [{}]:\n{}", label, String.join("\n", plan));

        // top-level Buffers line aggregates all child nodes
        return plan.stream()
                .filter(line -> line.contains("Buffers:"))
                .findFirst()
                .map(line -> parseValue(line, "shared hit=(\\d+)") + parseValue(line, "\\bread=(\\d+)"))
                .orElse(0);
    }

    private int parseValue(String line, String regex) {
        Matcher m = Pattern.compile(regex).matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String toMb(int blocks) {
        return String.format("%.0f MB", blocks * 8.0 / 1024);
    }

    private static String toKb(int blocks) {
        return String.format("%d KB", blocks * 8);
    }

    private static List<String> userIds(int from, int to) {
        return IntStream.range(from, to)
                .mapToObj(i -> "user_" + i)
                .toList();
    }

    private static void createSchema() {
        jdbc.execute("""
                CREATE TABLE auth_process (
                    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id     VARCHAR(36) NOT NULL,
                    auth_method VARCHAR(20) NOT NULL,
                    delivery_id VARCHAR(100),
                    status      VARCHAR(20) NOT NULL,
                    created_at  TIMESTAMP   NOT NULL,
                    updated_at  TIMESTAMP   NOT NULL,
                    expires_at  TIMESTAMP
                )
                """);
        log.info("Schema created");
    }

    private static void seedData() {
        log.info("Seeding {} rows via generate_series...", TOTAL_ROWS);
        long start = System.currentTimeMillis();
        jdbc.execute("""
                INSERT INTO auth_process (user_id, auth_method, delivery_id, status, created_at, updated_at, expires_at)
                SELECT
                    'user_' || (s %% %d),
                    'BIOMETRIC',
                    'delivery_' || s,
                    CASE (s %% 6)
                        WHEN 0 THEN 'PENDING'
                        WHEN 1 THEN 'APPROVED'
                        WHEN 2 THEN 'REJECTED'
                        WHEN 3 THEN 'EXPIRED'
                        WHEN 4 THEN 'CANCELLED'
                        ELSE        'CLOSED'
                    END,
                    NOW(),
                    NOW(),
                    NOW() + interval '5 minutes'
                FROM generate_series(1, %d) AS s
                """.formatted(DISTINCT_USERS, TOTAL_ROWS));
        log.info("Seeded {} rows in {}ms", TOTAL_ROWS, System.currentTimeMillis() - start);
    }
}