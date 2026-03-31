package pl.kathelan.dbperf;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
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

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
@Testcontainers
class AuthProcessUpdatePerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(AuthProcessUpdatePerformanceTest.class);

    private static final int TOTAL_ROWS     = 600_000;
    private static final int DISTINCT_USERS = 10_000;

    // user_6000: s=6000 → 6000%6=0 → PENDING, outside range of other tests
    private static final String EXPLAIN_USER = "user_6000";

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

    @ParameterizedTest(name = "N={0} single-row updates")
    @ValueSource(ints = {100, 1_000, 5_000})
    void singleRowUpdate_indexStrategiesComparison(int n) {
        // fetch N rows with VARIED statuses — delivery_id is unique → always 1 row per UPDATE
        List<String[]> targets = jdbc.query(
                "SELECT user_id, status, delivery_id FROM auth_process LIMIT ?",
                (rs, i) -> new String[]{rs.getString("user_id"), rs.getString("status"), rs.getString("delivery_id")},
                n);
        String explainUser   = targets.getFirst()[0];
        String explainStatus = targets.getFirst()[1];

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

    private int parseValue(String line, String regex) {
        Matcher m = Pattern.compile(regex).matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
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
