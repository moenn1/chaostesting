package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record LatencyTelemetrySnapshot(
        UUID id,
        UUID runId,
        String faultType,
        LatencyTelemetryPhase phase,
        int latencyMilliseconds,
        Integer latencyJitterMilliseconds,
        Integer latencyMinimumMilliseconds,
        Integer latencyMaximumMilliseconds,
        int trafficPercentage,
        Integer dropPercentage,
        boolean rollbackVerified,
        String message,
        Instant capturedAt
) {
}
