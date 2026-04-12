package pl.kathelan.user.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.kathelan.common.resilience.exception.CircuitOpenException;
import pl.kathelan.user.UserServiceBaseTest;
import pl.kathelan.user.api.dto.AddressDto;
import pl.kathelan.user.api.dto.CreateUserRequestDto;
import pl.kathelan.user.api.dto.UserDto;
import pl.kathelan.user.exception.UserAlreadyExistsException;
import pl.kathelan.user.exception.UserNotFoundException;
import pl.kathelan.user.exception.UserServiceException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Living documentation for UserController HTTP contract.
 *
 * <p>Verifies that the controller maps service results and exceptions
 * to the correct HTTP status codes and response body shapes.
 * UserService is stubbed — no SOAP stack involved.
 */
@DisplayName("UserController — HTTP contract")
class UserControllerTest extends UserServiceBaseTest {

    private static final AddressDto ADDRESS = new AddressDto("ul. Testowa 1", "Warsaw", "00-001", "Poland");

    @Nested
    @DisplayName("GET /users/{id}")
    class GetUser {

        /**
         * Happy path: service returns a user → 200 with full UserDto in $.data.
         */
        @Test
        @DisplayName("200 with user body when user exists")
        void returns200WithUser_whenUserExists() {
            when(userService.getUser("id-1")).thenReturn(
                    new UserDto("id-1", "Jan", "Kowalski", "jan@example.com", ADDRESS));

            doGet("/users/{id}", "id-1")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.id").isEqualTo("id-1")
                    .jsonPath("$.data.email").isEqualTo("jan@example.com")
                    .jsonPath("$.data.address.city").isEqualTo("Warsaw");
        }

        /**
         * User not found: service throws UserNotFoundException → 404 with errorCode USER_NOT_FOUND,
         * no $.data field in response.
         */
        @Test
        @DisplayName("404 with USER_NOT_FOUND when user does not exist")
        void returns404_whenUserNotFound() {
            when(userService.getUser("missing")).thenThrow(new UserNotFoundException("missing"));

            doGet("/users/{id}", "missing")
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("USER_NOT_FOUND")
                    .jsonPath("$.data").doesNotExist();
        }

        /**
         * Circuit breaker open: soap-service is unavailable → 503 SERVICE_UNAVAILABLE.
         * Lets clients distinguish transient outage from permanent errors.
         */
        @Test
        @DisplayName("503 with SERVICE_UNAVAILABLE when circuit breaker is open")
        void returns503_whenCircuitBreakerOpen() {
            when(userService.getUser(any())).thenThrow(new CircuitOpenException("soap-service"));

            doGet("/users/{id}", "any")
                    .expectStatus().isEqualTo(503)
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("SERVICE_UNAVAILABLE");
        }

        /**
         * Unknown SOAP error: service throws UserServiceException → 500 with the upstream errorCode.
         */
        @Test
        @DisplayName("500 with upstream errorCode when SOAP returns unexpected error")
        void returns500WithUpstreamErrorCode_whenSoapReturnsUnknownError() {
            when(userService.getUser(any())).thenThrow(
                    new UserServiceException("UNKNOWN_ERROR", "Unexpected upstream failure"));

            doGet("/users/{id}", "any")
                    .expectStatus().isEqualTo(500)
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("UNKNOWN_ERROR");
        }
    }

    @Nested
    @DisplayName("POST /users")
    class CreateUser {

        /**
         * Happy path: valid request body, service creates user → 201 with created UserDto in $.data.
         */
        @Test
        @DisplayName("201 with created user when request is valid")
        void returns201WithCreatedUser_whenRequestIsValid() {
            when(userService.createUser(any())).thenReturn(
                    new UserDto("new-id", "Anna", "Nowak", "anna@example.com", ADDRESS));

            doPost("/users", new CreateUserRequestDto("Anna", "Nowak", "anna@example.com", ADDRESS))
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.data.id").isEqualTo("new-id")
                    .jsonPath("$.data.email").isEqualTo("anna@example.com");
        }

        /**
         * Duplicate email: service throws UserAlreadyExistsException → 409 CONFLICT
         * with USER_ALREADY_EXISTS, no $.data.
         */
        @Test
        @DisplayName("409 with USER_ALREADY_EXISTS when email is already taken")
        void returns409_whenEmailAlreadyExists() {
            when(userService.createUser(any())).thenThrow(new UserAlreadyExistsException("dup@example.com"));

            doPost("/users", new CreateUserRequestDto("Jan", "Kowalski", "dup@example.com", ADDRESS))
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("USER_ALREADY_EXISTS")
                    .jsonPath("$.data").doesNotExist();
        }

        /**
         * Invalid body (blank names, invalid email, null address) → 400 VALIDATION_ERROR.
         * Bean Validation runs before the service is called.
         * Message must include the offending field name so the caller knows what to fix.
         */
        @Test
        @DisplayName("400 with VALIDATION_ERROR and field name in message when body fails bean validation")
        void returns400_whenRequestBodyIsInvalid() {
            doPost("/users", new CreateUserRequestDto("", "", "not-an-email", null))
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR")
                    .jsonPath("$.message").value(msg ->
                            assertThat((String) msg).contains("email").contains("firstName"));
        }

        /**
         * Circuit breaker open during create → 503 SERVICE_UNAVAILABLE.
         */
        @Test
        @DisplayName("503 with SERVICE_UNAVAILABLE when circuit breaker is open")
        void returns503_whenCircuitBreakerOpen() {
            when(userService.createUser(any())).thenThrow(new CircuitOpenException("soap-service"));

            doPost("/users", new CreateUserRequestDto("Jan", "Kowalski", "jan@example.com", ADDRESS))
                    .expectStatus().isEqualTo(503)
                    .expectBody()
                    .jsonPath("$.errorCode").isEqualTo("SERVICE_UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("GET /users?city=")
    class GetUsersByCity {

        /**
         * City with users → 200 with list in $.data.
         */
        @Test
        @DisplayName("200 with user list when city has users")
        void returns200WithList_whenCityHasUsers() {
            when(userService.getUsersByCity("Warsaw")).thenReturn(List.of(
                    new UserDto("id-1", "Jan", "Kowalski", "a@example.com", ADDRESS),
                    new UserDto("id-2", "Anna", "Nowak", "b@example.com", ADDRESS)));

            doGet("/users?city=Warsaw")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.length()").isEqualTo(2);
        }

        /**
         * City with no users → 200 with empty list (not 404 — absence is a valid result).
         */
        @Test
        @DisplayName("200 with empty list when no users in city")
        void returns200WithEmptyList_whenNoUsersInCity() {
            when(userService.getUsersByCity("Berlin")).thenReturn(List.of());

            doGet("/users?city=Berlin")
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.length()").isEqualTo(0);
        }
    }
}
