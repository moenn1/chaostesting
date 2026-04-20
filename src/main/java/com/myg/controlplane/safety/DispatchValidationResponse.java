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
        Integer latencyJitterMilliseconds,
        Integer latencyMinimumMilliseconds,
        Integer latencyMaximumMilliseconds,
        Integer errorCode,
        Integer trafficPercentage,
        Integer dropPercentage,
        List<String> routeFilters,
        UUID approvalId,
        long maxDurationSeconds,
        long maxLatencyMilliseconds,
        List<GuardrailViolation> violations
) {
    public DispatchValidationResponse {
        routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
        violations = List.copyOf(violations);
    }
}
