package pl.kathelan.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.InitProcessResponse;
import pl.kathelan.auth.api.dto.ProcessStatusResponse;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.auth.event.AuthProcessStateChangedEvent;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CountBasedCircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.PushStatus;
import pl.kathelan.soap.push.generated.SendPushResponse;
import pl.kathelan.soap.push.generated.SendStatus;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthProcessServiceSchedulerTest {

    @Mock
    private MobilePushClient mobilePushClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuthProcessServiceImpl service;

    @BeforeEach
    void setUp() {
        CountBasedCircuitBreaker cb = new CountBasedCircuitBreaker(
                "mobile-push", new CircuitBreakerConfig(5, Duration.ofSeconds(10), e -> true),
                new InMemoryCircuitBreakerStateRepository()
        );
        ResilientCaller resilientCaller = new ResilientCaller(
                cb, new RetryExecutor(), new RetryConfig(2, Duration.ZERO, 1.0, Set.of(RuntimeException.class))
        );
        service = new AuthProcessServiceImpl(new InMemoryAuthProcessRepository(), mobilePushClient, resilientCaller, eventPublisher);
    }

    // --- cancel publishes event ---

    @Test
    void cancel_publishesCancelledEvent() {
        stubSendPush("delivery-1");
        InitProcessResponse init = service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));

        service.cancel(UUID.fromString(init.processId()));

        ArgumentCaptor<AuthProcessStateChangedEvent> captor = ArgumentCaptor.forClass(AuthProcessStateChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().newState()).isEqualTo(ProcessState.CANCELLED);
        assertThat(captor.getValue().userId()).isEqualTo("user1");
    }

    // --- pollAndUpdatePushStatuses ---

    @Test
    void pollAndUpdatePushStatuses_approvesWhenPushApproved() {
        stubSendPush("delivery-1");
        InitProcessResponse init = service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));
        stubPushStatus("delivery-1", PushStatus.APPROVED);

        service.pollAndUpdatePushStatuses();

        assertThat(service.getStatus(UUID.fromString(init.processId())).state()).isEqualTo(ProcessState.APPROVED);
    }

    @Test
    void pollAndUpdatePushStatuses_rejectsWhenPushRejected() {
        stubSendPush("delivery-1");
        InitProcessResponse init = service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));
        stubPushStatus("delivery-1", PushStatus.REJECTED);

        service.pollAndUpdatePushStatuses();

        assertThat(service.getStatus(UUID.fromString(init.processId())).state()).isEqualTo(ProcessState.REJECTED);
    }

    @Test
    void pollAndUpdatePushStatuses_expiresWhenPushExpired() {
        stubSendPush("delivery-1");
        InitProcessResponse init = service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));
        stubPushStatus("delivery-1", PushStatus.EXPIRED);

        service.pollAndUpdatePushStatuses();

        assertThat(service.getStatus(UUID.fromString(init.processId())).state()).isEqualTo(ProcessState.EXPIRED);
    }

    @Test
    void pollAndUpdatePushStatuses_publishesApprovedEvent() {
        stubSendPush("delivery-1");
        service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));
        stubPushStatus("delivery-1", PushStatus.APPROVED);

        service.pollAndUpdatePushStatuses();

        ArgumentCaptor<AuthProcessStateChangedEvent> captor = ArgumentCaptor.forClass(AuthProcessStateChangedEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(e -> e.newState() == ProcessState.APPROVED);
    }

    @Test
    void pollAndUpdatePushStatuses_publishesExpiredEvent() {
        stubSendPush("delivery-1");
        service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));
        stubPushStatus("delivery-1", PushStatus.EXPIRED);

        service.pollAndUpdatePushStatuses();

        ArgumentCaptor<AuthProcessStateChangedEvent> captor = ArgumentCaptor.forClass(AuthProcessStateChangedEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(e -> e.newState() == ProcessState.EXPIRED);
    }

    @Test
    void pollAndUpdatePushStatuses_doesNotChangeStateWhenPushPending() {
        stubSendPush("delivery-1");
        InitProcessResponse init = service.initProcess(new InitProcessRequest("user1", AuthMethod.PUSH));
        stubPushStatus("delivery-1", PushStatus.PENDING);

        service.pollAndUpdatePushStatuses();

        assertThat(service.getStatus(UUID.fromString(init.processId())).state()).isEqualTo(ProcessState.PENDING);
        verify(eventPublisher, never()).publishEvent(anyString());
    }

    // --- helpers ---

    private void stubSendPush(String deliveryId) {
        SendPushResponse response = new SendPushResponse();
        response.setDeliveryId(deliveryId);
        response.setSendStatus(SendStatus.SENT);
        response.setExpiresAt(toXmlDateTime(LocalDateTime.now().plusMinutes(2)));
        when(mobilePushClient.sendPush(anyString(), anyString())).thenReturn(response);
    }

    private void stubPushStatus(String deliveryId, PushStatus status) {
        GetPushStatusResponse response = new GetPushStatusResponse();
        response.setDeliveryId(deliveryId);
        response.setPushStatus(status);
        when(mobilePushClient.getPushStatus(deliveryId)).thenReturn(response);
    }

    private static XMLGregorianCalendar toXmlDateTime(LocalDateTime dt) {
        try {
            GregorianCalendar gc = GregorianCalendar.from(dt.atZone(ZoneId.systemDefault()));
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }
}