package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record LatencyTelemetrySnapshot(
        UUID id,
        UUID runId,
        LatencyTelemetryPhase phase,
        int latencyMilliseconds,
        int trafficPercentage,
        boolean rollbackVerified,
        String message,
        Instant capturedAt
) {
}
