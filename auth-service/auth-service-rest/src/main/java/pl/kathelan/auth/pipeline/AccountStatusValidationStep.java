package pl.kathelan.auth.pipeline;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.kathelan.auth.api.dto.AccountStatus;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.exception.AccountNotEligibleException;

import java.util.List;

@Component
@Order(0)
public class AccountStatusValidationStep implements DeviceProcessingStep {

    @Override
    public List<DeviceDto> process(List<DeviceDto> devices, DeviceProcessingContext context) {
        AccountStatus status = context.accountStatus();
        if (status == AccountStatus.BLOCKED || status == AccountStatus.SUSPENDED) {
            throw new AccountNotEligibleException(context.userId(), status);
        }
        return devices;
    }
}
