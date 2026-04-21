package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunDiagnosticsResponse(
        UUID runId,
        Instant exportedAt,
        ChaosRunResponse run,
        RunMetricsResponse metrics,
        RunTraceResponse traces,
        List<LatencyTelemetrySnapshotResponse> telemetry,
        List<SafetyAuditRecordResponse> auditRecords
) {
}
