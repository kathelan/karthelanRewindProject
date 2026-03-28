package pl.kathelan.auth.concurrency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.auth.service.AuthProcessServiceImpl;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CountBasedCircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;
import pl.kathelan.common.util.XmlDateTimeUtils;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.push.generated.SendPushResponse;
import pl.kathelan.soap.push.generated.SendStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthConcurrencyTest {

    @Mock
    private MobilePushClient mobilePushClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private InMemoryAuthProcessRepository repository;
    private AuthProcessServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAuthProcessRepository();
        CountBasedCircuitBreaker cb = new CountBasedCircuitBreaker(
                "test-cb", new CircuitBreakerConfig(5, Duration.ofSeconds(10), e -> true),
                new InMemoryCircuitBreakerStateRepository()
        );
        ResilientCaller resilientCaller = new ResilientCaller(
                cb, new RetryExecutor(), new RetryConfig(1, Duration.ZERO, 1.0, Set.of(RuntimeException.class))
        );
        service = new AuthProcessServiceImpl(repository, mobilePushClient, resilientCaller, eventPublisher);

        SendPushResponse response = new SendPushResponse();
        response.setDeliveryId("delivery-concurrent");
        response.setSendStatus(SendStatus.SENT);
        response.setExpiresAt(XmlDateTimeUtils.toXmlGregorianCalendar(LocalDateTime.now().plusMinutes(2)));
        when(mobilePushClient.sendPush(anyString(), anyString())).thenReturn(response);
    }

    @Test
    void concurrentInitForSameUser_resultsInExactlyOnePending() throws InterruptedException {
        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();

        long pendingCount = repository.findAllPending().stream()
                .filter(p -> p.userId().equals("user1"))
                .count();

        assertThat(pendingCount)
                .as("Po dwóch równoległych initProcess dla tego samego userId — dokładnie 1 PENDING")
                .isEqualTo(1);
    }

    @Test
    void concurrentInitForSameUser_previousProcessIsClosedAfterNewInit() throws InterruptedException {
        // First sequential init — creates PENDING
        UUID firstId = UUID.fromString(service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH)).processId());

        // Second init — should close first and create new PENDING
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                startLatch.await();
                service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        }).start();

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(repository.findAllPending())
                .as("Dokładnie 1 PENDING po drugim init")
                .hasSize(1);

        assertThat(repository.findById(firstId))
                .hasValueSatisfying(p -> assertThat(p.processState()).isEqualTo(ProcessState.CLOSED));
    }
}
