package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record ChaosRun(
        UUID id,
        UUID experimentId,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        UUID approvalId,
        ChaosRunStatus status,
        Instant createdAt,
        Instant startedAt,
        RunTargetSnapshot targetSnapshot,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason
) {
}
