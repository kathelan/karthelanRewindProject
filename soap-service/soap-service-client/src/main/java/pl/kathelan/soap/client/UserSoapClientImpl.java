package pl.kathelan.soap.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.client.core.WebServiceTemplate;
import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.CreateUserResponse;
import pl.kathelan.soap.api.generated.GetUserRequest;
import pl.kathelan.soap.api.generated.GetUserResponse;
import pl.kathelan.soap.api.generated.GetUsersByCityRequest;
import pl.kathelan.soap.api.generated.GetUsersByCityResponse;

@Slf4j
@RequiredArgsConstructor
public class UserSoapClientImpl implements UserSoapClient {

    private final WebServiceTemplate webServiceTemplate;

    @Override
    public GetUserResponse getUser(String id) {
        log.debug("getUser: id={}", id);
        GetUserRequest request = new GetUserRequest();
        request.setId(id);
        GetUserResponse response = (GetUserResponse) webServiceTemplate.marshalSendAndReceive(request);
        log.debug("getUser: response received, errorCode={}", response.getErrorCode());
        return response;
    }

    @Override
    public CreateUserResponse createUser(CreateUserRequest request) {
        log.debug("createUser: email={}", request.getEmail());
        CreateUserResponse response = (CreateUserResponse) webServiceTemplate.marshalSendAndReceive(request);
        log.debug("createUser: response received, errorCode={}", response.getErrorCode());
        return response;
    }

    @Override
    public GetUsersByCityResponse getUsersByCity(String city) {
        log.debug("getUsersByCity: city={}", city);
        GetUsersByCityRequest request = new GetUsersByCityRequest();
        request.setCity(city);
        GetUsersByCityResponse response = (GetUsersByCityResponse) webServiceTemplate.marshalSendAndReceive(request);
        log.debug("getUsersByCity: found {} users", response.getUsers().size());
        return response;
    }
}
