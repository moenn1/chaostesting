package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChaosRun(
        UUID id,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        Integer errorCode,
        Integer trafficPercentage,
        List<String> routeFilters,
        UUID approvalId,
        ChaosRunStatus status,
        Instant createdAt,
        Instant rollbackScheduledAt,
        Instant rollbackVerifiedAt,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason
) {
    public ChaosRun {
        routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
    }
}
