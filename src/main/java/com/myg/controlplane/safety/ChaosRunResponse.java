package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record ChaosRunResponse(
        UUID id,
        ChaosRunStatus status,
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
        Instant createdAt,
        Instant rollbackScheduledAt,
        Instant rollbackVerifiedAt,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason
) {
    public static ChaosRunResponse from(ChaosRun run) {
        return new ChaosRunResponse(
                run.id(),
                run.status(),
                run.targetEnvironment(),
                run.targetSelector(),
                run.faultType(),
                run.requestedDurationSeconds(),
                run.latencyMilliseconds(),
                run.latencyJitterMilliseconds(),
                run.latencyMinimumMilliseconds(),
                run.latencyMaximumMilliseconds(),
                run.trafficPercentage(),
                run.dropPercentage(),
                run.approvalId(),
                run.createdAt(),
                run.rollbackScheduledAt(),
                run.rollbackVerifiedAt(),
                run.stopCommandIssuedAt(),
                run.stopCommandIssuedBy(),
                run.stopCommandReason()
        );
    }
}
