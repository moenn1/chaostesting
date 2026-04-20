package com.myg.controlplane.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "latency_telemetry_snapshots")
public class LatencyTelemetrySnapshotEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LatencyTelemetryPhase phase;

    @Column(nullable = false)
    private int latencyMilliseconds;

    @Column(nullable = false)
    private int trafficPercentage;

    @Column(nullable = false)
    private boolean rollbackVerified;

    @Column(nullable = false, length = 512)
    private String message;

    @Column(nullable = false)
    private Instant capturedAt;

    protected LatencyTelemetrySnapshotEntity() {
    }

    public LatencyTelemetrySnapshotEntity(UUID id,
                                          UUID runId,
                                          LatencyTelemetryPhase phase,
                                          int latencyMilliseconds,
                                          int trafficPercentage,
                                          boolean rollbackVerified,
                                          String message,
                                          Instant capturedAt) {
        this.id = id;
        this.runId = runId;
        this.phase = phase;
        this.latencyMilliseconds = latencyMilliseconds;
        this.trafficPercentage = trafficPercentage;
        this.rollbackVerified = rollbackVerified;
        this.message = message;
        this.capturedAt = capturedAt;
    }

    public static LatencyTelemetrySnapshotEntity injection(ChaosRun run, Instant capturedAt, String message) {
        return new LatencyTelemetrySnapshotEntity(
                UUID.randomUUID(),
                run.id(),
                LatencyTelemetryPhase.INJECTION,
                run.latencyMilliseconds() == null ? 0 : run.latencyMilliseconds(),
                run.trafficPercentage() == null ? 0 : run.trafficPercentage(),
                false,
                message,
                capturedAt
        );
    }

    public static LatencyTelemetrySnapshotEntity rollback(ChaosRun run, Instant capturedAt, String message) {
        return new LatencyTelemetrySnapshotEntity(
                UUID.randomUUID(),
                run.id(),
                LatencyTelemetryPhase.ROLLBACK,
                0,
                0,
                true,
                message,
                capturedAt
        );
    }

    public LatencyTelemetrySnapshot toDomain() {
        return new LatencyTelemetrySnapshot(
                id,
                runId,
                phase,
                latencyMilliseconds,
                trafficPercentage,
                rollbackVerified,
                message,
                capturedAt
        );
    }
}
