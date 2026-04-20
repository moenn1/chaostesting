package com.myg.controlplane.safety;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
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

    private UUID approvalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChaosRunStatus status;

    @Column(nullable = false)
    private Instant createdAt;

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
                          UUID approvalId,
                          ChaosRunStatus status,
                          Instant createdAt,
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
        this.approvalId = approvalId;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.targetSnapshotJson = targetSnapshotJson;
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

    public ChaosRun toDomain(ObjectMapper objectMapper) {
        return new ChaosRun(
                id,
                experimentId,
                targetEnvironment,
                targetSelector,
                faultType,
                requestedDurationSeconds,
                approvalId,
                status,
                createdAt,
                startedAt,
                readJson(objectMapper, targetSnapshotJson, RunTargetSnapshot.class),
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
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize chaos run payload", exception);
        }
    }
}
