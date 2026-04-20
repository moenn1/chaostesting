package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record ChaosRunResponse(
        UUID id,
        UUID experimentId,
        ChaosRunStatus status,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        Integer latencyMilliseconds,
        Integer trafficPercentage,
        UUID approvalId,
        Instant createdAt,
        Instant rollbackScheduledAt,
        Instant rollbackVerifiedAt,
        Instant startedAt,
        RunTargetSnapshot targetSnapshot,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason
) {
    public static ChaosRunResponse from(ChaosRun run) {
        return new ChaosRunResponse(
                run.id(),
                run.experimentId(),
                run.status(),
                run.targetEnvironment(),
                run.targetSelector(),
                run.faultType(),
                run.requestedDurationSeconds(),
                run.latencyMilliseconds(),
                run.trafficPercentage(),
                run.approvalId(),
                run.createdAt(),
                run.rollbackScheduledAt(),
                run.rollbackVerifiedAt(),
                run.startedAt(),
                run.targetSnapshot(),
                run.stopCommandIssuedAt(),
                run.stopCommandIssuedBy(),
                run.stopCommandReason()
        );
    }
}
