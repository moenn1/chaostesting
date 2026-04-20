package com.myg.controlplane.safety;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chaos_runs")
public class ChaosRunEntity {

    @Id
    private UUID id;

    private UUID experimentId;

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

    private Integer errorCode;

    private Integer trafficPercentage;

    private Integer dropPercentage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "chaos_run_route_filters", joinColumns = @JoinColumn(name = "run_id"))
    @OrderColumn(name = "route_filter_order")
    @Column(name = "route_filter", nullable = false)
    private List<String> routeFilters;

    private UUID approvalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChaosRunStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant endedAt;

    @Column(nullable = false)
    private Instant rollbackScheduledAt;

    private Instant rollbackVerifiedAt;

    @Column(nullable = false)
    private Instant startedAt;

    @Lob
    private String targetSnapshotJson;

    private Instant stopCommandIssuedAt;

    private String stopCommandIssuedBy;

    @Column(length = 512)
    private String stopCommandReason;

    protected ChaosRunEntity() {
    }

    public ChaosRunEntity(UUID id,
                          UUID experimentId,
                          String targetEnvironment,
                          String targetSelector,
                          String faultType,
                          long requestedDurationSeconds,
                          Integer latencyMilliseconds,
                          Integer latencyJitterMilliseconds,
                          Integer latencyMinimumMilliseconds,
                          Integer latencyMaximumMilliseconds,
                          Integer errorCode,
                          Integer trafficPercentage,
                          Integer dropPercentage,
                          List<String> routeFilters,
                          UUID approvalId,
                          ChaosRunStatus status,
                          Instant createdAt,
                          Instant endedAt,
                          Instant rollbackScheduledAt,
                          Instant rollbackVerifiedAt,
                          Instant startedAt,
                          String targetSnapshotJson,
                          Instant stopCommandIssuedAt,
                          String stopCommandIssuedBy,
                          String stopCommandReason) {
        this.id = id;
        this.experimentId = experimentId;
        this.targetEnvironment = targetEnvironment;
        this.targetSelector = targetSelector;
        this.faultType = faultType;
        this.requestedDurationSeconds = requestedDurationSeconds;
        this.latencyMilliseconds = latencyMilliseconds;
        this.latencyJitterMilliseconds = latencyJitterMilliseconds;
        this.latencyMinimumMilliseconds = latencyMinimumMilliseconds;
        this.latencyMaximumMilliseconds = latencyMaximumMilliseconds;
        this.errorCode = errorCode;
        this.trafficPercentage = trafficPercentage;
        this.dropPercentage = dropPercentage;
        this.routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
        this.approvalId = approvalId;
        this.status = status;
        this.createdAt = createdAt;
        this.endedAt = endedAt;
        this.rollbackScheduledAt = rollbackScheduledAt;
        this.rollbackVerifiedAt = rollbackVerifiedAt;
        this.startedAt = startedAt;
        this.targetSnapshotJson = targetSnapshotJson;
        this.stopCommandIssuedAt = stopCommandIssuedAt;
        this.stopCommandIssuedBy = stopCommandIssuedBy;
        this.stopCommandReason = stopCommandReason;
    }

    public boolean canBeStopped() {
        return status.canBeStopped();
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

    public void markStopped(String operator, String reason, Instant now) {
        status = ChaosRunStatus.STOPPED;
        endedAt = now;
        stopCommandIssuedAt = now;
        stopCommandIssuedBy = operator;
        stopCommandReason = reason;
    }

    public void markRolledBack(Instant now) {
        status = ChaosRunStatus.ROLLED_BACK;
        endedAt = now;
        rollbackVerifiedAt = now;
    }

    public void markFailed(Instant now) {
        status = ChaosRunStatus.FAILED;
        endedAt = now;
    }

    public ChaosRun toDomain(ObjectMapper objectMapper) {
        return new ChaosRun(
                id,
                experimentId,
                targetEnvironment,
                targetSelector,
                faultType,
                requestedDurationSeconds,
                latencyMilliseconds,
                latencyJitterMilliseconds,
                latencyMinimumMilliseconds,
                latencyMaximumMilliseconds,
                errorCode,
                trafficPercentage,
                dropPercentage,
                routeFilters,
                approvalId,
                status,
                createdAt,
                endedAt,
                rollbackScheduledAt,
                rollbackVerifiedAt,
                startedAt,
                readJson(objectMapper, targetSnapshotJson, RunTargetSnapshot.class),
                stopCommandIssuedAt,
                stopCommandIssuedBy,
                stopCommandReason
        );
    }

    public ChaosRun toDomain() {
        return new ChaosRun(
                id,
                experimentId,
                targetEnvironment,
                targetSelector,
                faultType,
                requestedDurationSeconds,
                latencyMilliseconds,
                latencyJitterMilliseconds,
                latencyMinimumMilliseconds,
                latencyMaximumMilliseconds,
                errorCode,
                trafficPercentage,
                dropPercentage,
                routeFilters,
                approvalId,
                status,
                createdAt,
                endedAt,
                rollbackScheduledAt,
                rollbackVerifiedAt,
                startedAt,
                null,
                stopCommandIssuedAt,
                stopCommandIssuedBy,
                stopCommandReason
        );
    }

    public static String writeJson(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize chaos run payload", exception);
        }
    }

    private static <T> T readJson(ObjectMapper objectMapper, String value, Class<T> type) {
        if (objectMapper == null || value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize chaos run payload", exception);
        }
    }
}
