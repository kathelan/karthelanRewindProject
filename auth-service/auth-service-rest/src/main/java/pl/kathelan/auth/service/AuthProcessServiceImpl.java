package pl.kathelan.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.kathelan.auth.api.dto.CapabilitiesResponse;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.InitProcessResponse;
import pl.kathelan.auth.api.dto.ProcessStatusResponse;
import pl.kathelan.auth.api.exception.AuthProcessNotFoundException;
import pl.kathelan.auth.domain.AuthProcess;
import pl.kathelan.auth.domain.repository.AuthProcessRepository;
import pl.kathelan.common.resilience.ResilientCaller;
import pl.kathelan.soap.client.MobilePushClient;
import pl.kathelan.soap.push.generated.GetUserCapabilitiesResponse;
import pl.kathelan.soap.push.generated.SendPushResponse;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AuthProcessServiceImpl implements AuthProcessService {

    private final AuthProcessRepository repository;
    private final MobilePushClient mobilePushClient;
    private final ResilientCaller resilientCaller;

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
        AuthProcess process = created.withDeliveryId(soap.getDeliveryId());

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
        repository.save(process.cancel());
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
}
