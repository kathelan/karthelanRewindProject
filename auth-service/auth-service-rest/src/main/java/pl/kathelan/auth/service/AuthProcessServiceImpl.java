package pl.kathelan.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import pl.kathelan.auth.api.dto.CapabilitiesResponse;
import pl.kathelan.auth.api.dto.DeviceDto;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.InitProcessResponse;
import pl.kathelan.auth.api.dto.ProcessStatusResponse;
import pl.kathelan.auth.api.exception.AuthProcessNotFoundException;
import pl.kathelan.auth.domain.AuthProcess;
import pl.kathelan.auth.domain.repository.AuthProcessRepository;
import pl.kathelan.auth.event.AuthProcessStateChangedEvent;
import pl.kathelan.auth.mapper.AuthProcessMapper;
import pl.kathelan.auth.mapper.CapabilitiesMapper;
import pl.kathelan.auth.pipeline.DeviceProcessingContext;
import pl.kathelan.auth.pipeline.DeviceProcessingPipeline;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.common.util.XmlDateTimeUtils;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.push.generated.GetPushStatusResponse;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushResponse;

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
    private final DeviceProcessingPipeline deviceProcessingPipeline;
    private final CapabilitiesMapper capabilitiesMapper;

    // Per-user lock: prevents two simultaneous initProcess for the same userId
    // from both finding no pending process and creating two concurrent PENDING entries.
    // Note: at JPA layer this will be replaced by optimistic locking + unique constraint.
    private final java.util.concurrent.ConcurrentHashMap<String, Object> userInitLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public CapabilitiesResponse getCapabilities(String userId) {
        log.info("getCapabilities: userId={}", userId);
        GetUserCapabilitiesResponse soap = resilientCaller.call(() -> mobilePushClient.getUserCapabilities(userId));
        CapabilitiesResponse mapped = capabilitiesMapper.toCapabilitiesResponse(soap);
        DeviceProcessingContext context = new DeviceProcessingContext(userId, mapped.accountStatus());
        List<DeviceDto> filteredDevices = deviceProcessingPipeline.execute(mapped.devices(), context);
        return new CapabilitiesResponse(mapped.userId(), mapped.active(), mapped.accountStatus(), mapped.authMethods(), filteredDevices);
    }

    @Override
    public InitProcessResponse initProcess(InitProcessRequest request) {
        log.info("initProcess: userId={}, method={}", request.userId(), request.preferredMethod());
        Object lock = userInitLocks.computeIfAbsent(request.userId(), k -> new Object());
        synchronized (lock) {
            repository.findPendingByUserId(request.userId())
                    .map(AuthProcess::close)
                    .ifPresent(this::saveAndPublish);

            AuthProcess created = AuthProcess.create(request.userId(), request.preferredMethod());
            SendPushResponse soap = resilientCaller.call(
                    () -> mobilePushClient.sendPush(request.userId(), created.id().toString())
            );
            LocalDateTime expiresAt = XmlDateTimeUtils.toLocalDateTime(soap.getExpiresAt());
            AuthProcess process = created.assignDelivery(soap.getDeliveryId(), expiresAt);

            repository.save(process);
            return new InitProcessResponse(process.id().toString(), expiresAt);
        }
    }

    @Override
    public ProcessStatusResponse getStatus(UUID processId) {
        return AuthProcessMapper.toResponse(findOrThrow(processId));
    }

    @Override
    public void cancel(UUID processId) {
        saveAndPublish(findOrThrow(processId).cancel());
    }

    @Override
    public void pollAndUpdatePushStatuses() {
        repository.findAllPending().parallelStream().forEach(process -> {
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

    @Override
    public void expireOverdueProcesses() {
        repository.findPendingExpiredBefore(LocalDateTime.now())
                .forEach(process -> saveAndPublish(process.expire()));
    }

}