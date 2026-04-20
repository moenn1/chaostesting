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
        UUID approvalId,
        Instant createdAt,
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
                run.approvalId(),
                run.createdAt(),
                run.stopCommandIssuedAt(),
                run.stopCommandIssuedBy(),
                run.stopCommandReason()
        );
    }
}
