package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record DispatchAuthorizationResponse(
        UUID dispatchId,
        String status,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        Integer latencyMilliseconds,
        Integer trafficPercentage,
        UUID approvalId,
        Instant authorizedAt
) {
}
