package com.myg.controlplane.safety;

import java.util.List;
import java.util.UUID;

public record DispatchValidationResponse(
        DispatchDecision decision,
        boolean allowed,
        boolean requiresApproval,
        String targetEnvironment,
        long requestedDurationSeconds,
        UUID approvalId,
        long maxDurationSeconds,
        List<GuardrailViolation> violations
) {
    public DispatchValidationResponse {
        violations = List.copyOf(violations);
    }
}
