package pl.kathelan.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * Living documentation for UserServiceImpl — the orchestration layer between
 * the REST API and the SOAP client.
 *
 * <p>Tests cover: SOAP response mapping, domain exception translation,
 * circuit breaker policy (domain errors excluded), and retry on transient failures.
 *
 * <p>CB config: threshold=3, Retry config: maxAttempts=2 (1 retry), no delay.
 * Domain exceptions (UserNotFoundException, UserAlreadyExistsException) do NOT
 * count as CB failures — only infrastructure failures do.
 */
@DisplayName("UserServiceImpl — orchestration and resilience")
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserSoapClient soapClient;
    @Mock private UserRestMapper mapper;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig cbConfig = new CircuitBreakerConfig(
                3,
                Duration.ofSeconds(10),
                e -> !(e instanceof UserNotFoundException || e instanceof UserAlreadyExistsException)
        );
        CountBasedCircuitBreaker cb = new CountBasedCircuitBreaker(
                "soap-service", cbConfig, new InMemoryCircuitBreakerStateRepository()
        );
        RetryConfig retryConfig = new RetryConfig(2, Duration.ZERO, 1.0, Set.of(RuntimeException.class));
        service = new UserServiceImpl(soapClient, mapper, new ResilientCaller(cb, new RetryExecutor(), retryConfig));
    }

    @Nested
    @DisplayName("getUser")
    class GetUser {

        /**
         * SOAP returns user → service maps it to UserDto via UserRestMapper.
         */
        @Test
        @DisplayName("returns mapped UserDto when SOAP returns user")
        void shouldReturnDto_whenSoapReturnsUser() {
            pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
            GetUserResponse response = mock(GetUserResponse.class);
            UserDto expected = mock(UserDto.class);
            when(response.getUser()).thenReturn(soapUser);
            when(soapClient.getUser("123")).thenReturn(response);
            when(mapper.toDto(soapUser)).thenReturn(expected);

            UserDto result = service.getUser("123");

            assertThat(result).isEqualTo(expected);
        }

        /**
         * SOAP returns USER_NOT_FOUND → service throws UserNotFoundException with the id.
         * This is a domain error, not an infrastructure failure.
         */
        @Test
        @DisplayName("throws UserNotFoundException when SOAP returns USER_NOT_FOUND")
        void shouldThrowUserNotFoundException_whenSoapReturnsUserNotFound() {
            GetUserResponse response = mock(GetUserResponse.class);
            when(response.getUser()).thenReturn(null);
            when(response.getErrorCode()).thenReturn(ErrorCode.USER_NOT_FOUND);
            when(soapClient.getUser("missing")).thenReturn(response);

            assertThatThrownBy(() -> service.getUser("missing"))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("missing");
        }

        /**
         * SOAP returns null errorCode (unexpected state) → service wraps it in UserServiceException.
         * Preserves the upstream message for diagnosability.
         */
        @Test
        @DisplayName("throws UserServiceException with upstream message when SOAP returns unknown error")
        void shouldThrowUserServiceException_whenSoapReturnsUnknownErrorCode() {
            GetUserResponse response = mock(GetUserResponse.class);
            when(response.getUser()).thenReturn(null);
            when(response.getErrorCode()).thenReturn(null);
            when(response.getMessage()).thenReturn("Unexpected server error");
            when(soapClient.getUser("id-x")).thenReturn(response);

            assertThatThrownBy(() -> service.getUser("id-x"))
                    .isInstanceOf(UserServiceException.class)
                    .hasMessageContaining("Unexpected server error");
        }

        /**
         * USER_NOT_FOUND is a domain error — should never open the circuit breaker.
         * CB threshold=3; after 5 USER_NOT_FOUND responses the circuit must stay CLOSED.
         */
        @Test
        @DisplayName("does not open circuit breaker on repeated USER_NOT_FOUND (domain error)")
        void shouldNotOpenCircuitBreaker_onRepeatedUserNotFound() {
            GetUserResponse response = mock(GetUserResponse.class);
            when(response.getUser()).thenReturn(null);
            when(response.getErrorCode()).thenReturn(ErrorCode.USER_NOT_FOUND);
            when(soapClient.getUser(any())).thenReturn(response);

            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> service.getUser("x")).isInstanceOf(UserNotFoundException.class);
            }

            pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
            GetUserResponse ok = mock(GetUserResponse.class);
            when(ok.getUser()).thenReturn(soapUser);
            when(soapClient.getUser("found")).thenReturn(ok);
            when(mapper.toDto(soapUser)).thenReturn(mock(UserDto.class));

            assertThat(service.getUser("found")).isNotNull();
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        /**
         * SOAP creates user → service maps response to UserDto.
         */
        @Test
        @DisplayName("returns mapped UserDto when SOAP creates user successfully")
        void shouldReturnDto_whenSoapCreatesUser() {
            CreateUserRequestDto dto = mock(CreateUserRequestDto.class);
            CreateUserRequest soapRequest = mock(CreateUserRequest.class);
            pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
            CreateUserResponse response = mock(CreateUserResponse.class);
            UserDto expected = mock(UserDto.class);
            when(mapper.toSoapRequest(dto)).thenReturn(soapRequest);
            when(response.getUser()).thenReturn(soapUser);
            when(soapClient.createUser(soapRequest)).thenReturn(response);
            when(mapper.toDto(soapUser)).thenReturn(expected);

            assertThat(service.createUser(dto)).isEqualTo(expected);
        }

        /**
         * SOAP returns USER_ALREADY_EXISTS → service throws UserAlreadyExistsException.
         * This is a domain error and must not count as a CB failure.
         */
        @Test
        @DisplayName("throws UserAlreadyExistsException when SOAP returns USER_ALREADY_EXISTS")
        void shouldThrowUserAlreadyExistsException_whenSoapReturnsDuplicate() {
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

        /**
         * SOAP returns unexpected error code → service wraps in UserServiceException.
         */
        @Test
        @DisplayName("throws UserServiceException when SOAP returns unexpected error")
        void shouldThrowUserServiceException_whenSoapReturnsUnknownError() {
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
    }

    @Nested
    @DisplayName("getUsersByCity")
    class GetUsersByCity {

        /**
         * SOAP returns non-empty list → service maps each element via UserRestMapper.
         */
        @Test
        @DisplayName("returns mapped list when SOAP returns users")
        void shouldReturnMappedList_whenSoapReturnsUsers() {
            pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
            GetUsersByCityResponse response = mock(GetUsersByCityResponse.class);
            UserDto expected = mock(UserDto.class);
            when(response.getUsers()).thenReturn(List.of(soapUser));
            when(soapClient.getUsersByCity("Warsaw")).thenReturn(response);
            when(mapper.toDto(soapUser)).thenReturn(expected);

            assertThat(service.getUsersByCity("Warsaw")).containsExactly(expected);
        }

        /**
         * SOAP returns null users list → service returns empty list (not NPE, not 404).
         */
        @Test
        @DisplayName("returns empty list when SOAP returns null users")
        void shouldReturnEmptyList_whenSoapReturnsNullUsers() {
            GetUsersByCityResponse response = mock(GetUsersByCityResponse.class);
            when(response.getUsers()).thenReturn(null);
            when(soapClient.getUsersByCity("Nowhere")).thenReturn(response);

            assertThat(service.getUsersByCity("Nowhere")).isEmpty();
        }

        /**
         * SOAP returns empty list → service returns empty list.
         */
        @Test
        @DisplayName("returns empty list when SOAP returns empty users list")
        void shouldReturnEmptyList_whenSoapReturnsEmptyList() {
            GetUsersByCityResponse response = mock(GetUsersByCityResponse.class);
            when(response.getUsers()).thenReturn(List.of());
            when(soapClient.getUsersByCity("EmptyCity")).thenReturn(response);

            assertThat(service.getUsersByCity("EmptyCity")).isEmpty();
        }
    }

    @Nested
    @DisplayName("CircuitBreaker")
    class CircuitBreakerBehavior {

        /**
         * After CB threshold (3) of infrastructure failures (RuntimeException),
         * the next call throws CircuitOpenException without hitting the SOAP client.
         *
         * CB counts one failure per exhausted retry sequence, not per individual attempt.
         * With maxAttempts=2 and threshold=3: need 3 service calls that exhaust retries.
         */
        @Test
        @DisplayName("opens and throws CircuitOpenException after repeated infrastructure failures")
        void shouldOpenCircuit_afterInfrastructureFailures() {
            when(soapClient.getUser(any())).thenThrow(new RuntimeException("connection refused"));

            for (int i = 0; i < 5; i++) {
                try { service.getUser("x"); } catch (Exception ignored) {}
            }

            assertThatThrownBy(() -> service.getUser("x"))
                    .isInstanceOf(CircuitOpenException.class);
        }
    }

    @Nested
    @DisplayName("Retry")
    class RetryBehavior {

        /**
         * First attempt fails with transient error, second attempt succeeds.
         * Service returns the result as if no failure occurred.
         */
        @Test
        @DisplayName("retries on transient failure and returns result on second attempt")
        void shouldRetry_andReturnResult_whenSecondAttemptSucceeds() {
            pl.kathelan.soap.api.generated.UserDto soapUser = mock(pl.kathelan.soap.api.generated.UserDto.class);
            GetUserResponse ok = mock(GetUserResponse.class);
            when(ok.getUser()).thenReturn(soapUser);
            when(mapper.toDto(soapUser)).thenReturn(mock(UserDto.class));

            AtomicInteger attempts = new AtomicInteger(0);
            when(soapClient.getUser("123")).thenAnswer(inv -> {
                if (attempts.incrementAndGet() < 2) throw new RuntimeException("transient");
                return ok;
            });

            assertThat(service.getUser("123")).isNotNull();
            verify(soapClient, times(2)).getUser("123");
        }
    }
}
