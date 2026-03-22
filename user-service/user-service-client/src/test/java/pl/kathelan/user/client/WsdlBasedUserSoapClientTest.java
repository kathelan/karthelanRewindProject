package pl.kathelan.user.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.kathelan.user.client.generated.Address;
import pl.kathelan.user.client.generated.CreateUserRequest;
import pl.kathelan.user.client.generated.CreateUserResponse;
import pl.kathelan.user.client.generated.ErrorCode;
import pl.kathelan.user.client.generated.GetUserRequest;
import pl.kathelan.user.client.generated.GetUserResponse;
import pl.kathelan.user.client.generated.GetUsersByCityRequest;
import pl.kathelan.user.client.generated.GetUsersByCityResponse;
import pl.kathelan.user.client.generated.UserDto;
import pl.kathelan.user.client.generated.UsersPort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WsdlBasedUserSoapClientTest {

    @Mock
    private UsersPort usersPort;

    private WsdlBasedUserSoapClient client;

    @BeforeEach
    void setUp() {
        client = new WsdlBasedUserSoapClient(usersPort);
    }

    @Test
    void getUser_shouldReturnUserWhenFound() {
        GetUserResponse response = new GetUserResponse();
        response.setUser(buildUserDto("id-1", "jan@example.com"));
        when(usersPort.getUser(any(GetUserRequest.class))).thenReturn(response);

        GetUserResponse result = client.getUser("id-1");

        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo("id-1");
        assertThat(result.getUser().getEmail()).isEqualTo("jan@example.com");
        verify(usersPort).getUser(any(GetUserRequest.class));
    }

    @Test
    void getUser_shouldReturnErrorResponseWhenNotFound() {
        GetUserResponse notFound = new GetUserResponse();
        notFound.setErrorCode(ErrorCode.USER_NOT_FOUND);
        notFound.setMessage("User not found");
        when(usersPort.getUser(any(GetUserRequest.class))).thenReturn(notFound);

        GetUserResponse result = client.getUser("missing");

        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void createUser_shouldReturnCreatedUser() {
        CreateUserResponse response = new CreateUserResponse();
        response.setUser(buildUserDto("new-id", "anna@example.com"));
        when(usersPort.createUser(any(CreateUserRequest.class))).thenReturn(response);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("anna@example.com");
        CreateUserResponse result = client.createUser(request);

        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo("new-id");
    }

    @Test
    void createUser_shouldReturnErrorWhenEmailDuplicate() {
        CreateUserResponse duplicate = new CreateUserResponse();
        duplicate.setErrorCode(ErrorCode.USER_ALREADY_EXISTS);
        duplicate.setMessage("User already exists");
        when(usersPort.createUser(any(CreateUserRequest.class))).thenReturn(duplicate);

        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("dup@example.com");
        CreateUserResponse result = client.createUser(request);

        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorCode()).isEqualTo(ErrorCode.USER_ALREADY_EXISTS);
    }

    @Test
    void getUsersByCity_shouldReturnMatchingUsers() {
        GetUsersByCityResponse response = new GetUsersByCityResponse();
        response.getUsers().addAll(List.of(
                buildUserDto("id-1", "a@example.com"),
                buildUserDto("id-2", "b@example.com")));
        when(usersPort.getUsersByCity(any(GetUsersByCityRequest.class))).thenReturn(response);

        GetUsersByCityResponse result = client.getUsersByCity("Warsaw");

        assertThat(result.getUsers()).hasSize(2);
        verify(usersPort).getUsersByCity(any(GetUsersByCityRequest.class));
    }

    @Test
    void getUsersByCity_shouldReturnEmptyListWhenNoMatch() {
        when(usersPort.getUsersByCity(any(GetUsersByCityRequest.class)))
                .thenReturn(new GetUsersByCityResponse());

        GetUsersByCityResponse result = client.getUsersByCity("Berlin");

        assertThat(result.getUsers()).isEmpty();
    }

    // ===== helpers =====

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
