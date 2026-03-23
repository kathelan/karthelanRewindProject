package pl.kathelan.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.kathelan.common.resilience.circuitbreaker.CircuitBreaker;
import pl.kathelan.common.resilience.retry.RetryConfig;
import pl.kathelan.common.resilience.retry.RetryExecutor;
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
    private final CircuitBreaker circuitBreaker;
    private final RetryExecutor retryExecutor;
    private final RetryConfig retryConfig;

    @Override
    public UserDto getUser(String id) {
        log.info("getUser: id={}", id);
        GetUserResponse response = retryExecutor.execute(
                () -> circuitBreaker.execute(() -> soapClient.getUser(id)),
                retryConfig
        );
        if (response.getUser() == null) {
            if (response.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new UserNotFoundException(id);
            }
            throw new UserServiceException(response.getErrorCode().value(), response.getMessage());
        }
        return mapper.toDto(response.getUser());
    }

    @Override
    public UserDto createUser(CreateUserRequestDto dto) {
        log.info("createUser: email={}", dto.email());
        CreateUserResponse response = retryExecutor.execute(
                () -> circuitBreaker.execute(() -> soapClient.createUser(mapper.toSoapRequest(dto))),
                retryConfig
        );
        if (response.getUser() == null) {
            if (response.getErrorCode() == ErrorCode.USER_ALREADY_EXISTS) {
                throw new UserAlreadyExistsException(dto.email());
            }
            throw new UserServiceException(response.getErrorCode().value(), response.getMessage());
        }
        return mapper.toDto(response.getUser());
    }

    @Override
    public List<UserDto> getUsersByCity(String city) {
        log.info("getUsersByCity: city={}", city);
        GetUsersByCityResponse response = retryExecutor.execute(
                () -> circuitBreaker.execute(() -> soapClient.getUsersByCity(city)),
                retryConfig
        );
        return response.getUsers().stream().map(mapper::toDto).toList();
    }
}
