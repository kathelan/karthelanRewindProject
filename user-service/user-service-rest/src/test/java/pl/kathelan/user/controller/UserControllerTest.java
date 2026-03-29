package pl.kathelan.user.controller;

import org.junit.jupiter.api.Test;
import pl.kathelan.common.resilience.exception.CircuitOpenException;
import pl.kathelan.user.UserServiceBaseTest;
import pl.kathelan.user.api.dto.AddressDto;
import pl.kathelan.user.api.dto.CreateUserRequestDto;
import pl.kathelan.user.api.dto.UserDto;
import pl.kathelan.user.exception.UserAlreadyExistsException;
import pl.kathelan.user.exception.UserNotFoundException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class UserControllerTest extends UserServiceBaseTest {

    private static final AddressDto ADDRESS = new AddressDto("ul. Testowa 1", "Warsaw", "00-001", "Poland");

    // --- GET /users/{id} ---

    @Test
    void getUser_returns200WithUser() {
        when(userService.getUser("id-1")).thenReturn(
                new UserDto("id-1", "Jan", "Kowalski", "jan@example.com", ADDRESS));

        doGet("/users/{id}", "id-1")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo("id-1")
                .jsonPath("$.data.email").isEqualTo("jan@example.com")
                .jsonPath("$.data.address.city").isEqualTo("Warsaw");
    }

    @Test
    void getUser_returns404WhenNotFound() {
        when(userService.getUser("missing")).thenThrow(new UserNotFoundException("missing"));

        doGet("/users/{id}", "missing")
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("USER_NOT_FOUND")
                .jsonPath("$.data").doesNotExist();
    }

    @Test
    void getUser_returns503WhenCircuitOpen() {
        when(userService.getUser(any())).thenThrow(new CircuitOpenException("soap-service"));

        doGet("/users/{id}", "any")
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("SERVICE_UNAVAILABLE");
    }

    // --- POST /users ---

    @Test
    void createUser_returns201WithCreatedUser() {
        when(userService.createUser(any())).thenReturn(
                new UserDto("new-id", "Anna", "Nowak", "anna@example.com", ADDRESS));

        doPost("/users", new CreateUserRequestDto("Anna", "Nowak", "anna@example.com", ADDRESS))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.id").isEqualTo("new-id")
                .jsonPath("$.data.email").isEqualTo("anna@example.com");
    }

    @Test
    void createUser_returns409WhenEmailAlreadyExists() {
        when(userService.createUser(any())).thenThrow(new UserAlreadyExistsException("dup@example.com"));

        doPost("/users", new CreateUserRequestDto("Jan", "Kowalski", "dup@example.com", ADDRESS))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("USER_ALREADY_EXISTS")
                .jsonPath("$.data").doesNotExist();
    }

    @Test
    void createUser_returns400WhenBodyInvalid() {
        doPost("/users", new CreateUserRequestDto("", "", "not-an-email", null))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR");
    }

    // --- GET /users?city= ---

    @Test
    void getUsersByCity_returns200WithList() {
        when(userService.getUsersByCity("Warsaw")).thenReturn(List.of(
                new UserDto("id-1", "Jan", "Kowalski", "a@example.com", ADDRESS),
                new UserDto("id-2", "Anna", "Nowak", "b@example.com", ADDRESS)));

        doGet("/users?city=Warsaw")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(2);
    }

    @Test
    void getUsersByCity_returns200WithEmptyList() {
        when(userService.getUsersByCity("Berlin")).thenReturn(List.of());

        doGet("/users?city=Berlin")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(0);
    }
}
