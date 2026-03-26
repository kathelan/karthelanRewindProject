package pl.kathelan.auth.service;

import pl.kathelan.auth.api.dto.CapabilitiesResponse;
import pl.kathelan.auth.api.dto.InitProcessRequest;
import pl.kathelan.auth.api.dto.InitProcessResponse;
import pl.kathelan.auth.api.dto.ProcessStatusResponse;

import java.util.UUID;

public interface AuthProcessService {
    CapabilitiesResponse getCapabilities(String userId);
    InitProcessResponse initProcess(InitProcessRequest request);
    ProcessStatusResponse getStatus(UUID processId);
    void cancel(UUID processId);
}