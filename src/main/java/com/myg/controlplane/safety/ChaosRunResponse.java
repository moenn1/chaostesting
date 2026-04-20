package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChaosRunResponse(
        UUID id,
        ChaosRunStatus status,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        Integer errorCode,
        Integer trafficPercentage,
        List<String> routeFilters,
        UUID approvalId,
        Instant createdAt,
        Instant rollbackScheduledAt,
        Instant rollbackVerifiedAt,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason
) {
    public ChaosRunResponse {
        routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
    }

    public static ChaosRunResponse from(ChaosRun run) {
        return new ChaosRunResponse(
                run.id(),
                run.status(),
                run.targetEnvironment(),
                run.targetSelector(),
                run.faultType(),
                run.requestedDurationSeconds(),
                run.errorCode(),
                run.trafficPercentage(),
                run.routeFilters(),
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
