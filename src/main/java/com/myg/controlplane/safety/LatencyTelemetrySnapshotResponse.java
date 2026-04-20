package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record LatencyTelemetrySnapshotResponse(
        UUID id,
        UUID runId,
        LatencyTelemetryPhase phase,
        int latencyMilliseconds,
        int trafficPercentage,
        boolean rollbackVerified,
        String message,
        Instant capturedAt
) {
    public static LatencyTelemetrySnapshotResponse from(LatencyTelemetrySnapshot snapshot) {
        return new LatencyTelemetrySnapshotResponse(
                snapshot.id(),
                snapshot.runId(),
                snapshot.phase(),
                snapshot.latencyMilliseconds(),
                snapshot.trafficPercentage(),
                snapshot.rollbackVerified(),
                snapshot.message(),
                snapshot.capturedAt()
        );
    }
}
