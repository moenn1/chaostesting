package com.myg.controlplane.safety;

import java.time.Instant;

public record RunMetricsSummaryResponse(
        long requestedDurationSeconds,
        long observedDurationSeconds,
        Instant startedAt,
        Instant lastObservedAt,
        Instant rollbackScheduledAt,
        Instant rollbackVerifiedAt,
        int telemetryPointCount,
        int injectionPointCount,
        int rollbackPointCount,
        int maxLatencyMilliseconds,
        int averageLatencyMilliseconds,
        int maxTrafficPercentage,
        boolean rollbackVerified
) {
}
