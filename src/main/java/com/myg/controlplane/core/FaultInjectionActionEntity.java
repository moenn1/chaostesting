package com.myg.controlplane.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fault_injection_actions")
public class FaultInjectionActionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID experimentRunId;

    private UUID agentId;

    @Column(nullable = false, length = 128)
    private String faultType;

    @Column(nullable = false)
    private String targetScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FaultInjectionActionPhase phase;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant appliedAt;

    private Instant completedAt;

    @Column(length = 1024)
    private String errorMessage;

    protected FaultInjectionActionEntity() {
    }

    public FaultInjectionActionEntity(UUID id,
                                      UUID experimentRunId,
                                      UUID agentId,
                                      String faultType,
                                      String targetScope,
                                      FaultInjectionActionPhase phase,
                                      Instant requestedAt,
                                      Instant appliedAt,
                                      Instant completedAt,
                                      String errorMessage) {
        this.id = id;
        this.experimentRunId = experimentRunId;
        this.agentId = agentId;
        this.faultType = faultType;
        this.targetScope = targetScope;
        this.phase = phase;
        this.requestedAt = requestedAt;
        this.appliedAt = appliedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
    }
}
