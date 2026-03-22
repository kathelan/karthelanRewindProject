package pl.kathelan.user.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.kathelan.soap.api.generated.CreateUserResponse;
import pl.kathelan.soap.api.generated.GetUserResponse;
import pl.kathelan.soap.api.generated.GetUsersByCityResponse;
import pl.kathelan.soap.client.UserSoapClient;
import pl.kathelan.user.api.dto.ApiResponse;
import pl.kathelan.user.api.dto.CreateUserRequestDto;
import pl.kathelan.user.api.dto.UserDto;
import pl.kathelan.user.mapper.UserRestMapper;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserSoapClient userSoapClient;
    private final UserRestMapper mapper;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable String id) {
        log.info("REST getUser: id={}", id);
        GetUserResponse soapResponse = userSoapClient.getUser(id);

        if (soapResponse.getUser() != null) {
            return ResponseEntity.ok(ApiResponse.success(mapper.toDto(soapResponse.getUser())));
        }

        log.warn("REST getUser: not found, id={}", id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        soapResponse.getErrorCode().value(),
                        soapResponse.getMessage()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> createUser(@RequestBody CreateUserRequestDto dto) {
        log.info("REST createUser: email={}", dto.email());
        CreateUserResponse soapResponse = userSoapClient.createUser(mapper.toSoapRequest(dto));

        if (soapResponse.getUser() != null) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(mapper.toDto(soapResponse.getUser())));
        }

        log.warn("REST createUser: failed, errorCode={}", soapResponse.getErrorCode());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        soapResponse.getErrorCode().value(),
                        soapResponse.getMessage()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsersByCity(@RequestParam String city) {
        log.info("REST getUsersByCity: city={}", city);
        GetUsersByCityResponse soapResponse = userSoapClient.getUsersByCity(city);

        List<UserDto> users = soapResponse.getUsers().stream()
                .map(mapper::toDto)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(users));
    }
}
