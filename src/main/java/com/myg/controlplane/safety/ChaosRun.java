package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChaosRun(
        UUID id,
        UUID experimentId,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        Integer latencyMilliseconds,
        Integer latencyJitterMilliseconds,
        Integer latencyMinimumMilliseconds,
        Integer latencyMaximumMilliseconds,
        Integer errorCode,
        Integer trafficPercentage,
        Integer dropPercentage,
        List<String> routeFilters,
        UUID approvalId,
        ChaosRunStatus status,
        Instant createdAt,
        Instant endedAt,
        Instant rollbackScheduledAt,
        Instant rollbackVerifiedAt,
        Instant startedAt,
        RunTargetSnapshot targetSnapshot,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason
) {
    public ChaosRun {
        routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
    }
}
