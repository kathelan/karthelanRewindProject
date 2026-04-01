package pl.kathelan.dbperf.oracle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Oracle view endpoint.
 * Requires docker-compose oracle-perf running on port 1521:
 * <pre>docker-compose -f db-performance/docker-compose.yml up oracle-perf -d</pre>
 *
 * <p>Run:
 * <pre>mvn test -pl db-performance -Dgroups=oracle -Dexcluded.test.groups=""</pre>
 */
@Tag("oracle")
@ActiveProfiles("oracle")
@SpringBootTest(classes = OracleTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OracleViewIntegrationTest {

    private static final String ENDPOINT = "/oracle/active-processes";

    @Autowired
    private TestRestTemplate restTemplate;

    private ActiveProcessDto[] body;

    @BeforeEach
    void fetchActiveProcesses() {
        var response = restTemplate.getForEntity(ENDPOINT, ActiveProcessDto[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        body = Objects.requireNonNull(response.getBody());
    }

    @Test
    void returnsOnlyInitiatedRows() {
        assertThat(body).allMatch(dto -> "INITIATED".equals(dto.status()));
    }

    @Test
    void containsExpectedSeedData() {
        assertThat(body).hasSize(3);
        assertThat(body)
                .extracting(ActiveProcessDto::authMethod)
                .containsExactlyInAnyOrder("PUSH", "PUSH", "SMS");
    }
}
