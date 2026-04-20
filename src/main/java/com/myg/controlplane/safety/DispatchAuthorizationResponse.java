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
        Integer latencyJitterMilliseconds,
        Integer latencyMinimumMilliseconds,
        Integer latencyMaximumMilliseconds,
        Integer trafficPercentage,
        Integer dropPercentage,
        UUID approvalId,
        Instant authorizedAt
) {
}
