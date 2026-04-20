package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;

public record DispatchValidationResponse(
        DispatchDecision decision,
        boolean allowed,
        boolean requiresApproval,
        String targetEnvironment,
        String faultType,
        long requestedDurationSeconds,
        Integer latencyMilliseconds,
        Integer trafficPercentage,
        UUID approvalId,
        long maxDurationSeconds,
        long maxLatencyMilliseconds,
        List<GuardrailViolation> violations
) {
    public DispatchValidationResponse {
        violations = List.copyOf(violations);
    }
}
