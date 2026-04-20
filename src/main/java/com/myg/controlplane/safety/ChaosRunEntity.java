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
@Table(name = "chaos_runs")
public class ChaosRunEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String targetEnvironment;

    @Column(nullable = false)
    private String targetSelector;

    @Column(nullable = false)
    private String faultType;

    @Column(nullable = false)
    private long requestedDurationSeconds;

    private UUID approvalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChaosRunStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant stopCommandIssuedAt;

    private String stopCommandIssuedBy;

    @Column(length = 512)
    private String stopCommandReason;

    protected ChaosRunEntity() {
    }

    public ChaosRunEntity(UUID id,
                          String targetEnvironment,
                          String targetSelector,
                          String faultType,
                          long requestedDurationSeconds,
                          UUID approvalId,
                          ChaosRunStatus status,
                          Instant createdAt,
                          Instant stopCommandIssuedAt,
                          String stopCommandIssuedBy,
                          String stopCommandReason) {
        this.id = id;
        this.targetEnvironment = targetEnvironment;
        this.targetSelector = targetSelector;
        this.faultType = faultType;
        this.requestedDurationSeconds = requestedDurationSeconds;
        this.approvalId = approvalId;
        this.status = status;
        this.createdAt = createdAt;
        this.stopCommandIssuedAt = stopCommandIssuedAt;
        this.stopCommandIssuedBy = stopCommandIssuedBy;
        this.stopCommandReason = stopCommandReason;
    }

    public void markStopRequested(String operator, String reason, Instant now) {
        if (status != ChaosRunStatus.ACTIVE) {
            return;
        }
        status = ChaosRunStatus.STOP_REQUESTED;
        stopCommandIssuedAt = now;
        stopCommandIssuedBy = operator;
        stopCommandReason = reason;
    }

    public ChaosRun toDomain() {
        return new ChaosRun(
                id,
                targetEnvironment,
                targetSelector,
                faultType,
                requestedDurationSeconds,
                approvalId,
                status,
                createdAt,
                stopCommandIssuedAt,
                stopCommandIssuedBy,
                stopCommandReason
        );
    }
}
