package pl.kathelan.soap.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.CreateUserResponse;
import pl.kathelan.soap.api.generated.ErrorCode;
import pl.kathelan.soap.api.generated.GetUserRequest;
import pl.kathelan.soap.api.generated.GetUserResponse;
import pl.kathelan.soap.api.generated.GetUsersByCityRequest;
import pl.kathelan.soap.api.generated.GetUsersByCityResponse;
import pl.kathelan.soap.domain.User;
import pl.kathelan.soap.exception.UserAlreadyExistsException;
import pl.kathelan.soap.mapper.UserMapper;
import pl.kathelan.soap.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Endpoint
@RequiredArgsConstructor
public class UserEndpoint {

    private static final String NAMESPACE = "http://kathelan.pl/soap/users";

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @PayloadRoot(namespace = NAMESPACE, localPart = "getUserRequest")
    @ResponsePayload
    public GetUserResponse getUser(@RequestPayload GetUserRequest request) {
        log.info("getUser: id={}", request.getId());
        GetUserResponse response = new GetUserResponse();
        Optional<User> user = userRepository.findById(request.getId());

        if (user.isPresent()) {
            response.setUser(userMapper.toDto(user.get()));
        } else {
            log.warn("getUser: user not found, id={}", request.getId());
            response.setErrorCode(ErrorCode.USER_NOT_FOUND);
            response.setMessage("User with id '%s' not found".formatted(request.getId()));
        }

        return response;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "createUserRequest")
    @ResponsePayload
    public CreateUserResponse createUser(@RequestPayload CreateUserRequest request) {
        log.info("createUser: email={}", request.getEmail());
        CreateUserResponse response = new CreateUserResponse();
        try {
            User saved = userRepository.save(userMapper.toDomain(request));
            log.info("createUser: created user with id={}", saved.getId());
            response.setUser(userMapper.toDto(saved));
        } catch (UserAlreadyExistsException e) {
            log.warn("createUser: duplicate email={}", request.getEmail());
            response.setErrorCode(ErrorCode.USER_ALREADY_EXISTS);
            response.setMessage(e.getMessage());
        }
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "getUsersByCityRequest")
    @ResponsePayload
    public GetUsersByCityResponse getUsersByCity(@RequestPayload GetUsersByCityRequest request) {
        log.info("getUsersByCity: city={}", request.getCity());
        GetUsersByCityResponse response = new GetUsersByCityResponse();
        List<User> users = userRepository.findByCity(request.getCity());
        log.debug("getUsersByCity: found {} users in city={}", users.size(), request.getCity());
        users.stream()
                .map(userMapper::toDto)
                .forEach(response.getUsers()::add);
        return response;
    }
}
