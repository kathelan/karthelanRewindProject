package pl.kathelan.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.kathelan.soap.api.generated.Address;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.CreateUserResponse;
import pl.kathelan.soap.api.generated.ErrorCode;
import pl.kathelan.soap.api.generated.GetUserResponse;
import pl.kathelan.soap.api.generated.GetUsersByCityResponse;
import pl.kathelan.soap.api.generated.UserDto;
import pl.kathelan.soap.client.UserSoapClient;
import pl.kathelan.user.api.dto.AddressDto;
import pl.kathelan.user.api.dto.CreateUserRequestDto;
import pl.kathelan.user.mapper.UserRestMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserSoapClient userSoapClient;

    @MockBean
    private UserRestMapper mapper;

    @Test
    void shouldReturn200WithUserWhenFound() throws Exception {
        when(userSoapClient.getUser("id-1")).thenReturn(getUserResponse("id-1", "jan@example.com"));
        when(mapper.toDto(any())).thenReturn(new pl.kathelan.user.api.dto.UserDto(
                "id-1", "Jan", "Kowalski", "jan@example.com",
                new AddressDto("ul. Testowa 1", "Warsaw", "00-001", "Poland")));

        mockMvc.perform(get("/users/id-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("id-1"))
                .andExpect(jsonPath("$.data.email").value("jan@example.com"))
                .andExpect(jsonPath("$.data.address.city").value("Warsaw"));
    }

    @Test
    void shouldReturn404WhenUserNotFound() throws Exception {
        GetUserResponse notFound = new GetUserResponse();
        notFound.setErrorCode(ErrorCode.USER_NOT_FOUND);
        notFound.setMessage("User not found");
        when(userSoapClient.getUser("missing")).thenReturn(notFound);

        mockMvc.perform(get("/users/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturn201WhenUserCreated() throws Exception {
        CreateUserResponse created = new CreateUserResponse();
        created.setUser(buildUserDto("new-id", "anna@example.com"));
        when(userSoapClient.createUser(any(CreateUserRequest.class))).thenReturn(created);
        when(mapper.toSoapRequest(any())).thenReturn(new CreateUserRequest());
        when(mapper.toDto(any())).thenReturn(new pl.kathelan.user.api.dto.UserDto(
                "new-id", "Anna", "Nowak", "anna@example.com",
                new AddressDto("ul. Kwiatowa 5", "Krakow", "30-001", "Poland")));

        CreateUserRequestDto requestDto = new CreateUserRequestDto(
                "Anna", "Nowak", "anna@example.com",
                new AddressDto("ul. Kwiatowa 5", "Krakow", "30-001", "Poland"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("new-id"))
                .andExpect(jsonPath("$.data.email").value("anna@example.com"));
    }

    @Test
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        CreateUserResponse duplicate = new CreateUserResponse();
        duplicate.setErrorCode(ErrorCode.USER_ALREADY_EXISTS);
        duplicate.setMessage("User already exists");
        when(userSoapClient.createUser(any(CreateUserRequest.class))).thenReturn(duplicate);
        when(mapper.toSoapRequest(any())).thenReturn(new CreateUserRequest());

        CreateUserRequestDto requestDto = new CreateUserRequestDto(
                "Jan", "Kowalski", "dup@example.com",
                new AddressDto("ul. Testowa 1", "Warsaw", "00-001", "Poland"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturn200WithListWhenGetByCity() throws Exception {
        GetUsersByCityResponse cityResponse = new GetUsersByCityResponse();
        cityResponse.getUsers().addAll(List.of(
                buildUserDto("id-1", "a@example.com"),
                buildUserDto("id-2", "b@example.com")));
        when(userSoapClient.getUsersByCity(eq("Warsaw"))).thenReturn(cityResponse);
        when(mapper.toDto(any())).thenReturn(
                new pl.kathelan.user.api.dto.UserDto("id-1", "A", "B", "a@example.com",
                        new AddressDto("ul.", "Warsaw", "00-001", "Poland")));

        mockMvc.perform(get("/users").param("city", "Warsaw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void shouldReturn200WithEmptyListWhenNoCityMatch() throws Exception {
        when(userSoapClient.getUsersByCity(eq("Berlin"))).thenReturn(new GetUsersByCityResponse());

        mockMvc.perform(get("/users").param("city", "Berlin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ===== helpers =====

    private GetUserResponse getUserResponse(String id, String email) {
        GetUserResponse response = new GetUserResponse();
        response.setUser(buildUserDto(id, email));
        return response;
    }

    private UserDto buildUserDto(String id, String email) {
        Address address = new Address();
        address.setStreet("ul. Testowa 1");
        address.setCity("Warsaw");
        address.setZipCode("00-001");
        address.setCountry("Poland");

        UserDto dto = new UserDto();
        dto.setId(id);
        dto.setFirstName("Jan");
        dto.setLastName("Kowalski");
        dto.setEmail(email);
        dto.setAddress(address);
        return dto;
    }
}
