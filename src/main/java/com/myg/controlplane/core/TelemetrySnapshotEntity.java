package com.myg.controlplane.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "telemetry_snapshots")
public class TelemetrySnapshotEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID experimentRunId;

    private UUID agentId;

    @Column(nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private boolean healthy;

    private Double latencyMs;

    private Double errorRate;

    private Double cpuPercent;

    private Double memoryPercent;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    private String summaryJson;

    protected TelemetrySnapshotEntity() {
    }

    public TelemetrySnapshotEntity(UUID id,
                                   UUID experimentRunId,
                                   UUID agentId,
                                   Instant capturedAt,
                                   boolean healthy,
                                   Double latencyMs,
                                   Double errorRate,
                                   Double cpuPercent,
                                   Double memoryPercent,
                                   String summaryJson) {
        this.id = id;
        this.experimentRunId = experimentRunId;
        this.agentId = agentId;
        this.capturedAt = capturedAt;
        this.healthy = healthy;
        this.latencyMs = latencyMs;
        this.errorRate = errorRate;
        this.cpuPercent = cpuPercent;
        this.memoryPercent = memoryPercent;
        this.summaryJson = summaryJson;
    }
}
