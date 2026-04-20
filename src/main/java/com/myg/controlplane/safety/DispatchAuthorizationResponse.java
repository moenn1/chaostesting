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
        UUID approvalId,
        Instant authorizedAt
) {
}
