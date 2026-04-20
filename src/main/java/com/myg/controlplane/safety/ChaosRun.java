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
        Integer latencyMilliseconds,
        Integer trafficPercentage,
        UUID approvalId,
        ChaosRunStatus status,
        Instant createdAt,
        Instant rollbackScheduledAt,
        Instant rollbackVerifiedAt,
        Instant startedAt,
        RunTargetSnapshot targetSnapshot,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason
) {
}
