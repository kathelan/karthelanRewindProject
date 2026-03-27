package pl.kathelan.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pl.kathelan.auth.api.dto.AuthMethod;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CountBasedCircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;
import pl.kathelan.auth.api.dto.CapabilitiesResponse;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.InitProcessResponse;
import pl.kathelan.auth.api.dto.ProcessState;
import pl.kathelan.auth.api.dto.ProcessStatusResponse;
import pl.kathelan.auth.api.exception.AuthProcessNotFoundException;
import pl.kathelan.auth.api.exception.InvalidStateTransitionException;
import pl.kathelan.auth.domain.AuthProcess;
import pl.kathelan.auth.domain.repository.InMemoryAuthProcessRepository;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthProcessServiceTest {

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

    // --- getCapabilities ---

    @Test
    void getCapabilitiesReturnsActiveUserWithMethods() {
        GetUserCapabilitiesResponse soapResponse = new GetUserCapabilitiesResponse();
        soapResponse.setUserId("user1");
        soapResponse.setActive(true);
        soapResponse.getAuthMethods().add(pl.kathelan.soap.push.generated.AuthMethod.PUSH);
        when(mobilePushClient.getUserCapabilities("user1")).thenReturn(soapResponse);

        CapabilitiesResponse result = service.getCapabilities("user1");

        assertThat(result.userId()).isEqualTo("user1");
        assertThat(result.active()).isTrue();
        assertThat(result.authMethods()).containsExactly(pl.kathelan.auth.api.dto.AuthMethod.PUSH);
    }

    // --- initProcess ---

    @Test
    void initProcessCreatesPendingProcess() {
        stubSendPush("delivery-1");

        InitProcessResponse response = service.initProcess(new InitProcessRequest("user1", pl.kathelan.auth.api.dto.AuthMethod.PUSH));

        assertThat(response.processId()).isNotNull();
    }

    @Test
    void initProcessClosesPreviousPendingForSameUser() {
        stubSendPush("delivery-1");
        InitProcessResponse first = service.initProcess(new InitProcessRequest("user1", pl.kathelan.auth.api.dto.AuthMethod.PUSH));

        stubSendPush("delivery-2");
        service.initProcess(new InitProcessRequest("user1", pl.kathelan.auth.api.dto.AuthMethod.PUSH));

        ProcessStatusResponse firstStatus = service.getStatus(UUID.fromString(first.processId()));
        assertThat(firstStatus.state()).isEqualTo(ProcessState.CLOSED);
    }

    @Test
    void initProcessForDifferentUsersAreIndependent() {
        stubSendPush("delivery-1");
        InitProcessResponse user1 = service.initProcess(new InitProcessRequest("user1", pl.kathelan.auth.api.dto.AuthMethod.PUSH));

        stubSendPush("delivery-2");
        service.initProcess(new InitProcessRequest("user2", pl.kathelan.auth.api.dto.AuthMethod.PUSH));

        assertThat(service.getStatus(UUID.fromString(user1.processId())).state()).isEqualTo(ProcessState.PENDING);
    }

    // --- getStatus ---

    @Test
    void getStatusReturnsPendingForNewProcess() {
        stubSendPush("delivery-1");
        InitProcessResponse init = service.initProcess(new InitProcessRequest("user1", pl.kathelan.auth.api.dto.AuthMethod.PUSH));

        ProcessStatusResponse status = service.getStatus(UUID.fromString(init.processId()));

        assertThat(status.state()).isEqualTo(ProcessState.PENDING);
        assertThat(status.userId()).isEqualTo("user1");
    }

    @Test
    void getStatusThrowsWhenProcessNotFound() {
        assertThatThrownBy(() -> service.getStatus(UUID.randomUUID()))
                .isInstanceOf(AuthProcessNotFoundException.class);
    }

    // --- cancel ---

    @Test
    void cancelTransitionsToCancelled() {
        stubSendPush("delivery-1");
        InitProcessResponse init = service.initProcess(new InitProcessRequest("user1", pl.kathelan.auth.api.dto.AuthMethod.PUSH));

        service.cancel(UUID.fromString(init.processId()));

        assertThat(service.getStatus(UUID.fromString(init.processId())).state()).isEqualTo(ProcessState.CANCELLED);
    }

    @Test
    void cancelThrowsWhenProcessNotFound() {
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID()))
                .isInstanceOf(AuthProcessNotFoundException.class);
    }

    @Test
    void cancelThrowsWhenProcessAlreadyTerminal() {
        stubSendPush("delivery-1");
        InitProcessResponse init = service.initProcess(new InitProcessRequest("user1", pl.kathelan.auth.api.dto.AuthMethod.PUSH));
        service.cancel(UUID.fromString(init.processId()));

        assertThatThrownBy(() -> service.cancel(UUID.fromString(init.processId())))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    // --- helpers ---

    private void stubSendPush(String deliveryId) {
        SendPushResponse response = new SendPushResponse();
        response.setDeliveryId(deliveryId);
        response.setSendStatus(SendStatus.SENT);
        response.setExpiresAt(toXmlDateTime(LocalDateTime.now().plusMinutes(2)));
        when(mobilePushClient.sendPush(anyString(), anyString())).thenReturn(response);
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