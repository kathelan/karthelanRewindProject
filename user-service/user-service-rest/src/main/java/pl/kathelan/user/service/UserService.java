package pl.kathelan.user.service;

import pl.kathelan.user.api.dto.CreateUserRequestDto;
import pl.kathelan.user.api.dto.UserDto;

import java.util.List;

public interface UserService {
    UserDto getUser(String id);
    UserDto createUser(CreateUserRequestDto dto);
    List<UserDto> getUsersByCity(String city);
}
