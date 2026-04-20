package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record LatencyTelemetrySnapshotResponse(
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
    public static LatencyTelemetrySnapshotResponse from(LatencyTelemetrySnapshot snapshot) {
        return new LatencyTelemetrySnapshotResponse(
                snapshot.id(),
                snapshot.runId(),
                snapshot.faultType(),
                snapshot.phase(),
                snapshot.latencyMilliseconds(),
                snapshot.latencyJitterMilliseconds(),
                snapshot.latencyMinimumMilliseconds(),
                snapshot.latencyMaximumMilliseconds(),
                snapshot.trafficPercentage(),
                snapshot.dropPercentage(),
                snapshot.rollbackVerified(),
                snapshot.message(),
                snapshot.capturedAt()
        );
    }
}
