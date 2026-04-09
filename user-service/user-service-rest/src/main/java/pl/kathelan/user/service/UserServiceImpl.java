package pl.kathelan.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.kathelan.common.resilience.ResilientCaller;
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

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserSoapClient soapClient;
    private final UserRestMapper mapper;
    private final ResilientCaller resilientCaller;

    @Override
    public UserDto getUser(String id) {
        log.info("getUser: id={}", id);
        GetUserResponse response = resilientCaller.call(() -> soapClient.getUser(id));
        if (response.getUser() == null) {
            if (response.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new UserNotFoundException(id);
            }
            throw new UserServiceException(
                    response.getErrorCode() != null ? response.getErrorCode().value() : "UNKNOWN_ERROR",
                    response.getMessage());
        }
        return mapper.toDto(response.getUser());
    }

    @Override
    public UserDto createUser(CreateUserRequestDto dto) {
        log.info("createUser: email={}", dto.email());
        CreateUserResponse response = resilientCaller.call(() -> soapClient.createUser(mapper.toSoapRequest(dto)));
        if (response.getUser() == null) {
            if (response.getErrorCode() == ErrorCode.USER_ALREADY_EXISTS) {
                throw new UserAlreadyExistsException(dto.email());
            }
            throw new UserServiceException(
                    response.getErrorCode() != null ? response.getErrorCode().value() : "UNKNOWN_ERROR",
                    response.getMessage());
        }
        return mapper.toDto(response.getUser());
    }

    @Override
    public List<UserDto> getUsersByCity(String city) {
        log.info("getUsersByCity: city={}", city);
        GetUsersByCityResponse response = resilientCaller.call(() -> soapClient.getUsersByCity(city));
        if (response.getUsers() == null) {
            return List.of();
        }
        return response.getUsers().stream().map(mapper::toDto).toList();
    }
}
