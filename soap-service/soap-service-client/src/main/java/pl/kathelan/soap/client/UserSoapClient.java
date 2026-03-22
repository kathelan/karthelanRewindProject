package pl.kathelan.soap.client;

import pl.kathelan.soap.api.generated.CreateUserRequest;
import pl.kathelan.soap.api.generated.CreateUserResponse;
import pl.kathelan.soap.api.generated.GetUserResponse;
import pl.kathelan.soap.api.generated.GetUsersByCityResponse;

public interface UserSoapClient {

    GetUserResponse getUser(String id);

    CreateUserResponse createUser(CreateUserRequest request);

    GetUsersByCityResponse getUsersByCity(String city);
}
