package com.myg.controlplane.safety;

import java.util.List;

record DispatchValidationResult(
        DispatchDecision decision,
        String normalizedEnvironment,
        List<GuardrailViolation> violations
) {
    DispatchValidationResult {
        violations = List.copyOf(violations);
    }

    DispatchValidationResponse toResponse(RunDispatchRequest request,
                                          long maxDurationSeconds,
                                          long maxLatencyMilliseconds) {
        return new DispatchValidationResponse(
                decision,
                decision == DispatchDecision.ALLOWED,
                decision == DispatchDecision.APPROVAL_REQUIRED,
                normalizedEnvironment,
                request.normalizedFaultType(),
                request.requestedDurationSeconds(),
                request.latencyMilliseconds(),
                request.trafficPercentage(),
                request.approvalId(),
                maxDurationSeconds,
                maxLatencyMilliseconds,
                violations
        );
    }
}
