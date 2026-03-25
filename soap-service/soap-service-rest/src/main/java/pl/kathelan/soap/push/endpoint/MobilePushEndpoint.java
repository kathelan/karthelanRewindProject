package pl.kathelan.soap.push.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import pl.kathelan.soap.push.domain.PushRecord;
import pl.kathelan.soap.push.domain.PushStatus;
import pl.kathelan.soap.push.domain.SendStatus;
import pl.kathelan.soap.push.domain.UserCapabilities;
import pl.kathelan.soap.push.generated.ErrorCode;
import pl.kathelan.soap.push.generated.GetPushStatusRequest;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesRequest;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushRequest;
import pl.kathelan.soap.push.generated.SendPushResponse;
import pl.kathelan.soap.push.mapper.MobilePushMapper;
import pl.kathelan.soap.push.repository.CapabilitiesRepository;
import pl.kathelan.soap.push.repository.PushRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Endpoint
@RequiredArgsConstructor
public class MobilePushEndpoint {

    private static final String NAMESPACE = "http://kathelan.pl/soap/push";

    private final CapabilitiesRepository capabilitiesRepository;
    private final PushRepository pushRepository;
    private final MobilePushMapper mapper;

    @PayloadRoot(namespace = NAMESPACE, localPart = "getUserCapabilitiesRequest")
    @ResponsePayload
    public GetUserCapabilitiesResponse getUserCapabilities(@RequestPayload GetUserCapabilitiesRequest request) {
        log.info("getUserCapabilities: userId={}", request.getUserId());
        Optional<UserCapabilities> capabilities = capabilitiesRepository.findByUserId(request.getUserId());

        if (capabilities.isEmpty()) {
            log.warn("getUserCapabilities: user not found, userId={}", request.getUserId());
            GetUserCapabilitiesResponse response = new GetUserCapabilitiesResponse();
            response.setErrorCode(ErrorCode.USER_NOT_FOUND);
            response.setMessage("User not found: '%s'".formatted(request.getUserId()));
            return response;
        }

        return mapper.toResponse(capabilities.get());
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "sendPushRequest")
    @ResponsePayload
    public SendPushResponse sendPush(@RequestPayload SendPushRequest request) {
        log.info("sendPush: userId={}, processId={}", request.getUserId(), request.getProcessId());
        SendPushResponse response = new SendPushResponse();

        Optional<UserCapabilities> capabilities = capabilitiesRepository.findByUserId(request.getUserId());
        if (capabilities.isEmpty()) {
            log.warn("sendPush: user not found, userId={}", request.getUserId());
            response.setErrorCode(ErrorCode.USER_NOT_FOUND);
            response.setMessage("User not found: '%s'".formatted(request.getUserId()));
            return response;
        }

        if (!capabilities.get().isActive()) {
            log.warn("sendPush: user inactive, userId={}", request.getUserId());
            response.setErrorCode(ErrorCode.USER_INACTIVE);
            response.setMessage("User is inactive: '%s'".formatted(request.getUserId()));
            return response;
        }

        String deliveryId = UUID.randomUUID().toString();
        pushRepository.save(PushRecord.builder()
                .deliveryId(deliveryId)
                .userId(request.getUserId())
                .processId(request.getProcessId())
                .status(PushStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("sendPush: push sent, deliveryId={}", deliveryId);
        response.setDeliveryId(deliveryId);
        response.setSendStatus(mapper.toGenerated(SendStatus.SENT));
        return response;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "getPushStatusRequest")
    @ResponsePayload
    public GetPushStatusResponse getPushStatus(@RequestPayload GetPushStatusRequest request) {
        log.info("getPushStatus: deliveryId={}", request.getDeliveryId());
        GetPushStatusResponse response = new GetPushStatusResponse();

        Optional<PushRecord> record = pushRepository.findByDeliveryId(request.getDeliveryId());
        if (record.isEmpty()) {
            log.warn("getPushStatus: delivery not found, deliveryId={}", request.getDeliveryId());
            response.setErrorCode(ErrorCode.DELIVERY_NOT_FOUND);
            response.setMessage("Delivery not found: '%s'".formatted(request.getDeliveryId()));
            return response;
        }

        response.setDeliveryId(record.get().getDeliveryId());
        response.setPushStatus(mapper.toGenerated(record.get().getStatus()));
        return response;
    }
}