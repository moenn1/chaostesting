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

    private Integer latencyMilliseconds;

    private Integer latencyJitterMilliseconds;

    private Integer latencyMinimumMilliseconds;

    private Integer latencyMaximumMilliseconds;

    private Integer trafficPercentage;

    private Integer dropPercentage;

    private UUID approvalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChaosRunStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant rollbackScheduledAt;

    private Instant rollbackVerifiedAt;

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
                          Integer latencyMilliseconds,
                          Integer latencyJitterMilliseconds,
                          Integer latencyMinimumMilliseconds,
                          Integer latencyMaximumMilliseconds,
                          Integer trafficPercentage,
                          Integer dropPercentage,
                          UUID approvalId,
                          ChaosRunStatus status,
                          Instant createdAt,
                          Instant rollbackScheduledAt,
                          Instant rollbackVerifiedAt,
                          Instant stopCommandIssuedAt,
                          String stopCommandIssuedBy,
                          String stopCommandReason) {
        this.id = id;
        this.targetEnvironment = targetEnvironment;
        this.targetSelector = targetSelector;
        this.faultType = faultType;
        this.requestedDurationSeconds = requestedDurationSeconds;
        this.latencyMilliseconds = latencyMilliseconds;
        this.latencyJitterMilliseconds = latencyJitterMilliseconds;
        this.latencyMinimumMilliseconds = latencyMinimumMilliseconds;
        this.latencyMaximumMilliseconds = latencyMaximumMilliseconds;
        this.trafficPercentage = trafficPercentage;
        this.dropPercentage = dropPercentage;
        this.approvalId = approvalId;
        this.status = status;
        this.createdAt = createdAt;
        this.rollbackScheduledAt = rollbackScheduledAt;
        this.rollbackVerifiedAt = rollbackVerifiedAt;
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

    public void markRolledBack(Instant now) {
        status = ChaosRunStatus.ROLLED_BACK;
        rollbackVerifiedAt = now;
    }

    public ChaosRun toDomain() {
        return new ChaosRun(
                id,
                targetEnvironment,
                targetSelector,
                faultType,
                requestedDurationSeconds,
                latencyMilliseconds,
                latencyJitterMilliseconds,
                latencyMinimumMilliseconds,
                latencyMaximumMilliseconds,
                trafficPercentage,
                dropPercentage,
                approvalId,
                status,
                createdAt,
                rollbackScheduledAt,
                rollbackVerifiedAt,
                stopCommandIssuedAt,
                stopCommandIssuedBy,
                stopCommandReason
        );
    }
}
