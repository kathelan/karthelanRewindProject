package pl.kathelan.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import pl.kathelan.auth.api.dto.CapabilitiesResponse;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.InitProcessResponse;
import pl.kathelan.auth.api.dto.ProcessStatusResponse;
import pl.kathelan.auth.api.exception.AuthProcessNotFoundException;
import pl.kathelan.auth.domain.AuthProcess;
import pl.kathelan.auth.domain.repository.AuthProcessRepository;
import pl.kathelan.auth.event.AuthProcessStateChangedEvent;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushResponse;

import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AuthProcessServiceImpl implements AuthProcessService, AuthProcessSchedulerService {

    private final AuthProcessRepository repository;
    private final MobilePushClient mobilePushClient;
    private final ResilientCaller resilientCaller;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public CapabilitiesResponse getCapabilities(String userId) {
        log.info("getCapabilities: userId={}", userId);
        GetUserCapabilitiesResponse soap = resilientCaller.call(() -> mobilePushClient.getUserCapabilities(userId));
        List<pl.kathelan.auth.api.dto.AuthMethod> methods = soap.getAuthMethods().stream()
                .map(m -> pl.kathelan.auth.api.dto.AuthMethod.valueOf(m.name()))
                .toList();
        return new CapabilitiesResponse(soap.getUserId(), soap.isActive(), methods);
    }

    @Override
    public InitProcessResponse initProcess(InitProcessRequest request) {
        log.info("initProcess: userId={}, method={}", request.userId(), request.preferredMethod());
        repository.findPendingByUserId(request.userId())
                .map(AuthProcess::close)
                .ifPresent(repository::save);

        AuthProcess created = AuthProcess.create(request.userId(), request.preferredMethod());
        SendPushResponse soap = resilientCaller.call(
                () -> mobilePushClient.sendPush(request.userId(), created.id().toString())
        );
        LocalDateTime expiresAt = fromXmlDateTime(soap.getExpiresAt());
        AuthProcess process = created.withDeliveryId(soap.getDeliveryId(), expiresAt);

        repository.save(process);
        return new InitProcessResponse(process.id().toString());
    }

    @Override
    public ProcessStatusResponse getStatus(UUID processId) {
        AuthProcess process = findOrThrow(processId);
        return toResponse(process);
    }

    @Override
    public void cancel(UUID processId) {
        AuthProcess process = findOrThrow(processId);
        AuthProcess cancelled = process.cancel();
        repository.save(cancelled);
        eventPublisher.publishEvent(new AuthProcessStateChangedEvent(cancelled.id(), cancelled.userId(), cancelled.processState()));
    }

    @Override
    public void pollAndUpdatePushStatuses() {
        repository.findAllPending().forEach(process -> {
            if (process.deliveryId() == null) return;
            GetPushStatusResponse statusResponse = resilientCaller.call(
                    () -> mobilePushClient.getPushStatus(process.deliveryId())
            );
            switch (statusResponse.getPushStatus()) {
                case APPROVED -> saveAndPublish(process.approve());
                case REJECTED -> saveAndPublish(process.reject());
                case EXPIRED  -> saveAndPublish(process.expire());
                default -> { /* PENDING — brak akcji */ }
            }
        });
    }

    private void saveAndPublish(AuthProcess process) {
        repository.save(process);
        eventPublisher.publishEvent(new AuthProcessStateChangedEvent(process.id(), process.userId(), process.processState()));
    }

    private AuthProcess findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new AuthProcessNotFoundException(id));
    }

    private ProcessStatusResponse toResponse(AuthProcess p) {
        return new ProcessStatusResponse(
                p.id().toString(),
                p.userId(),
                p.processState(),
                p.authMethod(),
                p.createdAt(),
                p.updatedAt()
        );
    }

    private static LocalDateTime fromXmlDateTime(XMLGregorianCalendar xgc) {
        if (xgc == null) return null;
        return xgc.toGregorianCalendar().toZonedDateTime().toLocalDateTime();
    }
}