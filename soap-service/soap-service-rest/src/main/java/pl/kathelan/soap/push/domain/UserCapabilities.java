package pl.kathelan.soap.push.domain;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class UserCapabilities {
    String userId;
    boolean active;
    List<AuthMethod> authMethods;
    AccountStatus accountStatus;
    List<DeviceInfo> devices;
}