package com.myg.controlplane.safety;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChaosRunDispatchService {

    private final SafetyGuardrailsService safetyGuardrailsService;
    private final ChaosRunService chaosRunService;

    public ChaosRunDispatchService(SafetyGuardrailsService safetyGuardrailsService,
                                   ChaosRunService chaosRunService) {
        this.safetyGuardrailsService = safetyGuardrailsService;
        this.chaosRunService = chaosRunService;
    }

    @Transactional
    public DispatchAuthorizationResponse createRun(RunDispatchRequest request) {
        String requestedBy = request.requestedBy() == null || request.requestedBy().isBlank()
                ? "agent-runtime"
                : request.requestedBy().trim();
        return createRun(requestedBy, request);
    }

    @Transactional
    public DispatchAuthorizationResponse createRun(String requestedBy, RunDispatchRequest request) {
        String normalizedRequestedBy = requestedBy.trim();
        RunDispatchRequest sanitizedRequest = new RunDispatchRequest(
                request.targetEnvironment(),
                request.targetSelector(),
                request.faultType(),
                request.requestedDurationSeconds(),
                request.latencyMilliseconds(),
                request.latencyJitterMilliseconds(),
                request.latencyMinimumMilliseconds(),
                request.latencyMaximumMilliseconds(),
                request.errorCode(),
                request.trafficPercentage(),
                request.dropPercentage(),
                request.routeFilters(),
                request.approvalId(),
                normalizedRequestedBy
        );
        DispatchAuthorizationResponse authorization =
                safetyGuardrailsService.authorize(normalizedRequestedBy, sanitizedRequest);
        chaosRunService.startAuthorizedRun(authorization, sanitizedRequest);
        return authorization;
    }
}
