package pl.kathelan.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.kathelan.common.resilience.exception.CircuitOpenException;
import pl.kathelan.user.api.dto.AddressDto;
import pl.kathelan.user.api.dto.CreateUserRequestDto;
import pl.kathelan.user.api.dto.UserDto;
import pl.kathelan.user.exception.UserAlreadyExistsException;
import pl.kathelan.user.exception.UserNotFoundException;
import pl.kathelan.user.service.UserService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserService userService;

    private static final AddressDto ADDRESS = new AddressDto("ul. Testowa 1", "Warsaw", "00-001", "Poland");

    @Test
    void shouldReturn200WithUser_whenFound() throws Exception {
        when(userService.getUser("id-1")).thenReturn(
                new UserDto("id-1", "Jan", "Kowalski", "jan@example.com", ADDRESS));

        mockMvc.perform(get("/users/id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("id-1"))
                .andExpect(jsonPath("$.data.email").value("jan@example.com"))
                .andExpect(jsonPath("$.data.address.city").value("Warsaw"));
    }

    @Test
    void shouldReturn404_whenUserNotFound() throws Exception {
        when(userService.getUser("missing")).thenThrow(new UserNotFoundException("missing"));

        mockMvc.perform(get("/users/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturn201_whenUserCreated() throws Exception {
        when(userService.createUser(any())).thenReturn(
                new UserDto("new-id", "Anna", "Nowak", "anna@example.com", ADDRESS));

        CreateUserRequestDto request = new CreateUserRequestDto(
                "Anna", "Nowak", "anna@example.com", ADDRESS);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("new-id"))
                .andExpect(jsonPath("$.data.email").value("anna@example.com"));
    }

    @Test
    void shouldReturn409_whenEmailAlreadyExists() throws Exception {
        when(userService.createUser(any())).thenThrow(new UserAlreadyExistsException("dup@example.com"));

        CreateUserRequestDto request = new CreateUserRequestDto(
                "Jan", "Kowalski", "dup@example.com", ADDRESS);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturn200WithList_whenGetByCity() throws Exception {
        when(userService.getUsersByCity("Warsaw")).thenReturn(List.of(
                new UserDto("id-1", "Jan", "Kowalski", "a@example.com", ADDRESS),
                new UserDto("id-2", "Anna", "Nowak", "b@example.com", ADDRESS)));

        mockMvc.perform(get("/users").param("city", "Warsaw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void shouldReturn200WithEmptyList_whenNoCityMatch() throws Exception {
        when(userService.getUsersByCity("Berlin")).thenReturn(List.of());

        mockMvc.perform(get("/users").param("city", "Berlin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void shouldReturn503_whenCircuitOpen() throws Exception {
        when(userService.getUser(any())).thenThrow(new CircuitOpenException("soap-service"));

        mockMvc.perform(get("/users/any"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }
}
