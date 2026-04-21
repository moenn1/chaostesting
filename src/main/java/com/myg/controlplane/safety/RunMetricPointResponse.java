package com.myg.controlplane.safety;

import java.time.Instant;

public record RunMetricPointResponse(
        String faultType,
        Instant capturedAt,
        LatencyTelemetryPhase phase,
        int latencyMilliseconds,
        Integer latencyJitterMilliseconds,
        Integer latencyMinimumMilliseconds,
        Integer latencyMaximumMilliseconds,
        int trafficPercentage,
        Integer dropPercentage,
        boolean rollbackVerified,
        String message
) {
    public static RunMetricPointResponse from(LatencyTelemetrySnapshotResponse snapshot) {
        return new RunMetricPointResponse(
                snapshot.faultType(),
                snapshot.capturedAt(),
                snapshot.phase(),
                snapshot.latencyMilliseconds(),
                snapshot.latencyJitterMilliseconds(),
                snapshot.latencyMinimumMilliseconds(),
                snapshot.latencyMaximumMilliseconds(),
                snapshot.trafficPercentage(),
                snapshot.dropPercentage(),
                snapshot.rollbackVerified(),
                snapshot.message()
        );
    }
}
