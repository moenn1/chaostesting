package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record ChaosRun(
        UUID id,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        UUID approvalId,
        ChaosRunStatus status,
        Instant createdAt,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason
) {
}
