package pl.kathelan.auth.performance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.kathelan.auth.AuthServiceBaseTest;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.domain.AuthProcess;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.auth.scheduler.AuthProcessScheduler;
import pl.kathelan.auth.service.AuthProcessSchedulerService;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.PushStatus;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Sequential baseline for pollAndUpdatePushStatuses.
 * Documents O(N) growth before any parallelisation refactor.
 *
 * Run manually: mvn test -Dgroups=performance -pl auth-service/auth-service-rest
 * Excluded from normal CI via surefire <excludedGroups>performance</excludedGroups>.
 */
@Tag("performance")
class PollPerformanceTest extends AuthServiceBaseTest {

    private static final Logger log = LoggerFactory.getLogger(PollPerformanceTest.class);
    private static final int SIMULATED_SOAP_LATENCY_MS = 50;

    /** Replaces the real scheduler bean — prevents background polling from interfering with measurements. */
    @MockitoBean
    @SuppressWarnings("unused")
    private AuthProcessScheduler schedulerDisabled;

    @Autowired
    private InMemoryAuthProcessRepository repository;

    @Autowired
    private AuthProcessSchedulerService schedulerService;

    @ParameterizedTest(name = "sequential baseline — N={0} PENDING processes @ 50ms/call")
    @ValueSource(ints = {10, 50, 100})
    void pollAndUpdatePushStatuses_sequentialBaseline(int n) throws InterruptedException {
        // given — seed N PENDING processes with deliveryIds directly into repository
        for (int i = 0; i < n; i++) {
            AuthProcess process = AuthProcess.create("perf-user-" + i, AuthMethod.PUSH)
                    .assignDelivery("perf-delivery-" + i, LocalDateTime.now().plusMinutes(10));
            repository.save(process);
        }

        when(mobilePushClient.getPushStatus(anyString())).thenAnswer(inv -> {
            Thread.sleep(SIMULATED_SOAP_LATENCY_MS);
            GetPushStatusResponse response = new GetPushStatusResponse();
            response.setPushStatus(PushStatus.PENDING);
            return response;
        });

        // when
        long start = System.nanoTime();
        schedulerService.pollAndUpdatePushStatuses();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // then — parallel: elapsed should be much less than N * latency
        long sequentialMinMs = (long) n * SIMULATED_SOAP_LATENCY_MS;
        log.info("[PERF] N={} | elapsed={}ms | sequential_would_be={}ms | speedup={}x",
                n, elapsedMs, sequentialMinMs, sequentialMinMs / Math.max(elapsedMs, 1));

        assertThat(elapsedMs)
                .as("Parallel polling N=%d should complete faster than sequential (%dms)", n, sequentialMinMs)
                .isLessThan(sequentialMinMs);
    }
}