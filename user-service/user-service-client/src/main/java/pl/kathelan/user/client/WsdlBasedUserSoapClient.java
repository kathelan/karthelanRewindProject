package pl.kathelan.user.client;

import jakarta.xml.ws.BindingProvider;
import lombok.extern.slf4j.Slf4j;
import pl.kathelan.user.client.generated.CreateUserRequest;
import pl.kathelan.user.client.generated.CreateUserResponse;
import pl.kathelan.user.client.generated.GetUserRequest;
import pl.kathelan.user.client.generated.GetUserResponse;
import pl.kathelan.user.client.generated.GetUsersByCityRequest;
import pl.kathelan.user.client.generated.GetUsersByCityResponse;
import pl.kathelan.user.client.generated.UsersPort;
import pl.kathelan.user.client.generated.UsersPortService;

import java.net.URL;
import java.util.List;

/**
 * JAX-WS WSDL-based client for the SOAP Users service.
 * Uses wsimport-generated stubs — no WebServiceTemplate needed.
 * WSDL is loaded from classpath (wsdl/users.wsdl) to avoid hardcoded paths.
 */
@Slf4j
public class WsdlBasedUserSoapClient {

    private final UsersPort port;

    public WsdlBasedUserSoapClient(String endpointUrl, String username, String password) {
        URL wsdlUrl = WsdlBasedUserSoapClient.class.getClassLoader().getResource("wsdl/users.wsdl");
        if (wsdlUrl == null) {
            throw new IllegalStateException("WSDL not found on classpath: wsdl/users.wsdl");
        }
        UsersPortService service = new UsersPortService(wsdlUrl);
        this.port = service.getUsersPortSoap11();

        BindingProvider bp = (BindingProvider) port;
        bp.getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, endpointUrl);
        bp.getBinding().setHandlerChain(List.of(new WsSecurityHandler(username, password)));

        log.info("WsdlBasedUserSoapClient initialized, endpoint={}", endpointUrl);
    }

    /** Package-private constructor for unit testing with a mock port. */
    WsdlBasedUserSoapClient(UsersPort port) {
        this.port = port;
    }

    public GetUserResponse getUser(String id) {
        log.debug("getUser: id={}", id);
        GetUserRequest request = new GetUserRequest();
        request.setId(id);
        GetUserResponse response = port.getUser(request);
        log.debug("getUser: found={}", response.getUser() != null);
        return response;
    }

    public CreateUserResponse createUser(CreateUserRequest request) {
        log.debug("createUser: email={}", request.getEmail());
        CreateUserResponse response = port.createUser(request);
        log.debug("createUser: errorCode={}", response.getErrorCode());
        return response;
    }

    public GetUsersByCityResponse getUsersByCity(String city) {
        log.debug("getUsersByCity: city={}", city);
        GetUsersByCityRequest request = new GetUsersByCityRequest();
        request.setCity(city);
        GetUsersByCityResponse response = port.getUsersByCity(request);
        log.debug("getUsersByCity: count={}", response.getUsers().size());
        return response;
    }
}
