package pl.kathelan.auth.pipeline;

import pl.kathelan.auth.api.dto.AccountStatus;

public record DeviceProcessingContext(String userId, AccountStatus accountStatus) {
}
