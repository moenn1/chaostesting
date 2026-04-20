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
        DispatchAuthorizationResponse authorization = safetyGuardrailsService.authorize(request);
        chaosRunService.startAuthorizedRun(authorization, request);
        return authorization;
    }
}
