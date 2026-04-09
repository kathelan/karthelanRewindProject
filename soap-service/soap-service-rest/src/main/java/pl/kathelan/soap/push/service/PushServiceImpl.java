package pl.kathelan.soap.push.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.kathelan.common.util.XmlDateTimeUtils;
import pl.kathelan.soap.push.domain.PushRecord;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.domain.SendStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;
import pl.kathelan.soap.push.exception.DeliveryNotFoundException;
import pl.kathelan.soap.push.exception.UserInactiveException;
import pl.kathelan.soap.push.exception.UserNotFoundException;
import pl.kathelan.soap.push.generated.ErrorCode;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushResponse;
import pl.kathelan.soap.push.mapper.MobilePushMapper;
import pl.kathelan.soap.push.repository.CapabilitiesRepository;
import pl.kathelan.soap.push.repository.PushRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushServiceImpl implements PushService {

    private final CapabilitiesRepository capabilitiesRepository;
    private final PushRepository pushRepository;
    private final MobilePushMapper mapper;

    @Override
    public GetUserCapabilitiesResponse getUserCapabilities(String userId) {
        log.info("getUserCapabilities: userId={}", userId);
        return capabilitiesRepository.findByUserId(userId)
                .map(mapper::toResponse)
                .orElseGet(() -> {
                    log.warn("getUserCapabilities: user not found, userId={}", userId);
                    GetUserCapabilitiesResponse response = new GetUserCapabilitiesResponse();
                    response.setErrorCode(ErrorCode.USER_NOT_FOUND);
                    response.setMessage("User not found: '%s'".formatted(userId));
                    return response;
                });
    }

    @Override
    public SendPushResponse sendPush(String userId, String processId) {
        log.info("sendPush: userId={}, processId={}", userId, processId);

        UserCapabilities capabilities = capabilitiesRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!capabilities.isActive()) {
            throw new UserInactiveException(userId);
        }

        String deliveryId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(2);

        pushRepository.save(PushRecord.builder()
                .deliveryId(deliveryId)
                .userId(userId)
                .processId(processId)
                .status(PushStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build());

        log.info("sendPush: push sent, deliveryId={}", deliveryId);

        SendPushResponse response = new SendPushResponse();
        response.setDeliveryId(deliveryId);
        response.setSendStatus(mapper.toGenerated(SendStatus.SENT));
        response.setExpiresAt(XmlDateTimeUtils.toXmlGregorianCalendar(expiresAt));
        return response;
    }

    @Override
    public GetPushStatusResponse getPushStatus(String deliveryId) {
        log.info("getPushStatus: deliveryId={}", deliveryId);

        PushRecord record = pushRepository.findByDeliveryId(deliveryId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));

        GetPushStatusResponse response = new GetPushStatusResponse();
        response.setDeliveryId(record.getDeliveryId());
        response.setPushStatus(mapper.toGenerated(record.getStatus()));
        return response;
    }
}
