package com.myg.controlplane.safety;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChaosRunDispatchService {

    private final ChaosRunService chaosRunService;

    public ChaosRunDispatchService(ChaosRunService chaosRunService) {
        this.chaosRunService = chaosRunService;
    }

    @Transactional
    public DispatchAuthorizationResponse createRun(RunDispatchRequest request) {
        String requestedBy = request.requestedBy() == null || request.requestedBy().isBlank()
                ? "agent-runtime"
                : request.requestedBy().trim();
        return chaosRunService.createRun(requestedBy, request);
    }
}
