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

    @Column(nullable = false)
    private String faultType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LatencyTelemetryPhase phase;

    @Column(nullable = false)
    private int latencyMilliseconds;

    private Integer latencyJitterMilliseconds;

    private Integer latencyMinimumMilliseconds;

    private Integer latencyMaximumMilliseconds;

    @Column(nullable = false)
    private int trafficPercentage;

    private Integer dropPercentage;

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
                                          Instant capturedAt) {
        this.id = id;
        this.runId = runId;
        this.faultType = faultType;
        this.phase = phase;
        this.latencyMilliseconds = latencyMilliseconds;
        this.latencyJitterMilliseconds = latencyJitterMilliseconds;
        this.latencyMinimumMilliseconds = latencyMinimumMilliseconds;
        this.latencyMaximumMilliseconds = latencyMaximumMilliseconds;
        this.trafficPercentage = trafficPercentage;
        this.dropPercentage = dropPercentage;
        this.rollbackVerified = rollbackVerified;
        this.message = message;
        this.capturedAt = capturedAt;
    }

    public static LatencyTelemetrySnapshotEntity injection(ChaosRun run, Instant capturedAt, String message) {
        return new LatencyTelemetrySnapshotEntity(
                UUID.randomUUID(),
                run.id(),
                run.faultType(),
                LatencyTelemetryPhase.INJECTION,
                resolveLatencyMilliseconds(run),
                run.latencyJitterMilliseconds(),
                run.latencyMinimumMilliseconds(),
                run.latencyMaximumMilliseconds(),
                run.trafficPercentage() == null ? 0 : run.trafficPercentage(),
                run.dropPercentage(),
                false,
                message,
                capturedAt
        );
    }

    public static LatencyTelemetrySnapshotEntity rollback(ChaosRun run, Instant capturedAt, String message) {
        return new LatencyTelemetrySnapshotEntity(
                UUID.randomUUID(),
                run.id(),
                run.faultType(),
                LatencyTelemetryPhase.ROLLBACK,
                resolveLatencyMilliseconds(run),
                run.latencyJitterMilliseconds(),
                run.latencyMinimumMilliseconds(),
                run.latencyMaximumMilliseconds(),
                run.trafficPercentage() == null ? 0 : run.trafficPercentage(),
                run.dropPercentage(),
                true,
                message,
                capturedAt
        );
    }

    private static int resolveLatencyMilliseconds(ChaosRun run) {
        if (run.latencyMilliseconds() != null) {
            return run.latencyMilliseconds();
        }
        if (run.latencyMaximumMilliseconds() != null) {
            return run.latencyMaximumMilliseconds();
        }
        return 0;
    }

    public LatencyTelemetrySnapshot toDomain() {
        return new LatencyTelemetrySnapshot(
                id,
                runId,
                faultType,
                phase,
                latencyMilliseconds,
                latencyJitterMilliseconds,
                latencyMinimumMilliseconds,
                latencyMaximumMilliseconds,
                trafficPercentage,
                dropPercentage,
                rollbackVerified,
                message,
                capturedAt
        );
    }
}
