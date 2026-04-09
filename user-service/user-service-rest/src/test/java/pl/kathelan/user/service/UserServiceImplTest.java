package pl.kathelan.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreakerConfig;
import pl.kathelan.common.resilience.circuitbreaker.CountBasedCircuitBreaker;
import pl.kathelan.common.resilience.circuitbreaker.InMemoryCircuitBreakerStateRepository;
import pl.kathelan.common.resilience.exception.CircuitOpenException;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.CreateUserResponse;
import pl.kathelan.soap.api.generated.ErrorCode;
import pl.kathelan.soap.api.generated.GetUserResponse;
import pl.kathelan.soap.api.generated.GetUsersByCityResponse;
import pl.kathelan.soap.client.UserSoapClient;
import pl.kathelan.user.api.dto.CreateUserRequestDto;
import pl.kathelan.user.api.dto.UserDto;
import pl.kathelan.user.exception.UserAlreadyExistsException;
import pl.kathelan.user.exception.UserNotFoundException;
import pl.kathelan.user.exception.UserServiceException;
import pl.kathelan.user.mapper.UserRestMapper;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserSoapClient soapClient;
    @Mock private UserRestMapper mapper;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        // CB: otwiera po 3 błędach infrastrukturalnych, nie liczy błędów domenowych
        CircuitBreakerConfig cbConfig = new CircuitBreakerConfig(
                3,
                Duration.ofSeconds(10),
                e -> !(e instanceof UserNotFoundException || e instanceof UserAlreadyExistsException)
        );
        CountBasedCircuitBreaker cb = new CountBasedCircuitBreaker(
                "soap-service", cbConfig, new InMemoryCircuitBreakerStateRepository()
        );

        // Retry: 2 próby, bez opóźnienia (testy), tylko na RuntimeException
        RetryConfig retryConfig = new RetryConfig(2, Duration.ZERO, 1.0, Set.of(RuntimeException.class));
        RetryExecutor retryExecutor = new RetryExecutor();

        service = new UserServiceImpl(soapClient, mapper, new ResilientCaller(cb, retryExecutor, retryConfig));
    }

    // ===== getUser =====

    @Test
    void getUser_shouldReturnDto_whenFound() {
        pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
        GetUserResponse response = mock(GetUserResponse.class);
        UserDto expected = mock(UserDto.class);
        when(response.getUser()).thenReturn(soapUser);
        when(soapClient.getUser("123")).thenReturn(response);
        when(mapper.toDto(soapUser)).thenReturn(expected);

        UserDto result = service.getUser("123");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void getUser_shouldThrowUserNotFoundException_whenNotFound() {
        GetUserResponse response = mock(GetUserResponse.class);
        when(response.getUser()).thenReturn(null);
        when(response.getErrorCode()).thenReturn(ErrorCode.USER_NOT_FOUND);
        when(soapClient.getUser("missing")).thenReturn(response);

        assertThatThrownBy(() -> service.getUser("missing"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getUser_shouldThrowUserServiceException_whenUnknownErrorCode() {
        GetUserResponse response = mock(GetUserResponse.class);
        when(response.getUser()).thenReturn(null);
        when(response.getErrorCode()).thenReturn(null);
        when(response.getMessage()).thenReturn("Unexpected server error");
        when(soapClient.getUser("id-x")).thenReturn(response);

        assertThatThrownBy(() -> service.getUser("id-x"))
                .isInstanceOf(UserServiceException.class)
                .hasMessageContaining("Unexpected server error");
    }

    @Test
    void getUser_shouldNotCountUserNotFoundAsCbFailure() {
        GetUserResponse response = mock(GetUserResponse.class);
        when(response.getUser()).thenReturn(null);
        when(response.getErrorCode()).thenReturn(ErrorCode.USER_NOT_FOUND);
        when(soapClient.getUser(any())).thenReturn(response);

        // 5x USER_NOT_FOUND — CB nie powinien się otworzyć (threshold=3)
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.getUser("x")).isInstanceOf(UserNotFoundException.class);
        }

        // CB dalej CLOSED — normalne wywołanie przechodzi
        pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
        GetUserResponse ok = mock(GetUserResponse.class);
        when(ok.getUser()).thenReturn(soapUser);
        when(soapClient.getUser("found")).thenReturn(ok);
        when(mapper.toDto(soapUser)).thenReturn(mock(UserDto.class));

        assertThat(service.getUser("found")).isNotNull();
    }

    // ===== createUser =====

    @Test
    void createUser_shouldReturnDto_whenCreated() {
        CreateUserRequestDto dto = mock(CreateUserRequestDto.class);
        CreateUserRequest soapRequest = mock(CreateUserRequest.class);
        pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
        CreateUserResponse response = mock(CreateUserResponse.class);
        UserDto expected = mock(UserDto.class);
        when(mapper.toSoapRequest(dto)).thenReturn(soapRequest);
        when(response.getUser()).thenReturn(soapUser);
        when(soapClient.createUser(soapRequest)).thenReturn(response);
        when(mapper.toDto(soapUser)).thenReturn(expected);

        UserDto result = service.createUser(dto);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void createUser_shouldThrowUserAlreadyExistsException_whenDuplicate() {
        CreateUserRequestDto dto = mock(CreateUserRequestDto.class);
        CreateUserRequest soapRequest = mock(CreateUserRequest.class);
        CreateUserResponse response = mock(CreateUserResponse.class);
        when(mapper.toSoapRequest(dto)).thenReturn(soapRequest);
        when(response.getUser()).thenReturn(null);
        when(response.getErrorCode()).thenReturn(ErrorCode.USER_ALREADY_EXISTS);
        when(soapClient.createUser(soapRequest)).thenReturn(response);

        assertThatThrownBy(() -> service.createUser(dto))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void createUser_shouldThrowUserServiceException_whenUnknownErrorCode() {
        CreateUserRequestDto dto = mock(CreateUserRequestDto.class);
        CreateUserRequest soapRequest = mock(CreateUserRequest.class);
        CreateUserResponse response = mock(CreateUserResponse.class);
        when(mapper.toSoapRequest(dto)).thenReturn(soapRequest);
        when(response.getUser()).thenReturn(null);
        when(response.getErrorCode()).thenReturn(null);
        when(response.getMessage()).thenReturn("Upstream failure");
        when(soapClient.createUser(soapRequest)).thenReturn(response);

        assertThatThrownBy(() -> service.createUser(dto))
                .isInstanceOf(UserServiceException.class)
                .hasMessageContaining("Upstream failure");
    }

    // ===== getUsersByCity =====

    @Test
    void getUsersByCity_shouldReturnList() {
        pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
        GetUsersByCityResponse response = mock(GetUsersByCityResponse.class);
        UserDto expected = mock(UserDto.class);
        when(response.getUsers()).thenReturn(List.of(soapUser));
        when(soapClient.getUsersByCity("Warsaw")).thenReturn(response);
        when(mapper.toDto(soapUser)).thenReturn(expected);

        List<UserDto> result = service.getUsersByCity("Warsaw");

        assertThat(result).containsExactly(expected);
    }

    @Test
    void getUsersByCity_shouldReturnEmptyList_whenSoapReturnsNull() {
        GetUsersByCityResponse response = mock(GetUsersByCityResponse.class);
        when(response.getUsers()).thenReturn(null);
        when(soapClient.getUsersByCity("Nowhere")).thenReturn(response);

        List<UserDto> result = service.getUsersByCity("Nowhere");

        assertThat(result).isEmpty();
    }

    @Test
    void getUsersByCity_shouldReturnEmptyList_whenNoCityMatches() {
        GetUsersByCityResponse response = mock(GetUsersByCityResponse.class);
        when(response.getUsers()).thenReturn(List.of());
        when(soapClient.getUsersByCity("EmptyCity")).thenReturn(response);

        List<UserDto> result = service.getUsersByCity("EmptyCity");

        assertThat(result).isEmpty();
    }

    // ===== Circuit Breaker =====

    @Test
    void shouldOpenCircuitBreaker_afterInfrastructureFailures() {
        when(soapClient.getUser(any())).thenThrow(new RuntimeException("connection refused"));

        // CB(Retry): CB liczy 1 failure dopiero gdy Retry wyczerpie próby.
        // threshold=3, retry=2 → potrzeba 3 wywołań serwisu (każde wyczerpuje retry) żeby CB się otworzył.
        for (int i = 0; i < 5; i++) {
            try { service.getUser("x"); } catch (Exception ignored) {}
        }

        assertThatThrownBy(() -> service.getUser("x"))
                .isInstanceOf(CircuitOpenException.class);
    }

    // ===== Retry =====

    @Test
    void shouldRetryOnInfrastructureFailure_thenSucceed() {
        pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
        GetUserResponse ok = mock(GetUserResponse.class);
        when(ok.getUser()).thenReturn(soapUser);
        when(mapper.toDto(soapUser)).thenReturn(mock(UserDto.class));

        AtomicInteger attempts = new AtomicInteger(0);
        when(soapClient.getUser("123")).thenAnswer(inv -> {
            if (attempts.incrementAndGet() < 2) throw new RuntimeException("transient");
            return ok;
        });

        UserDto result = service.getUser("123");

        assertThat(result).isNotNull();
        verify(soapClient, times(2)).getUser("123");
    }
}
