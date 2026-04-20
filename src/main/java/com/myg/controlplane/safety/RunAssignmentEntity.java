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
@Table(name = "run_assignments")
public class RunAssignmentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID runId;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private String agentName;

    @Column(nullable = false)
    private String hostname;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunAssignmentStatus status;

    @Column(nullable = false)
    private Instant assignedAt;

    private Instant stopRequestedAt;

    private Instant endedAt;

    protected RunAssignmentEntity() {
    }

    public RunAssignmentEntity(UUID id,
                               UUID runId,
                               UUID agentId,
                               String agentName,
                               String hostname,
                               String environment,
                               String region,
                               RunAssignmentStatus status,
                               Instant assignedAt,
                               Instant stopRequestedAt,
                               Instant endedAt) {
        this.id = id;
        this.runId = runId;
        this.agentId = agentId;
        this.agentName = agentName;
        this.hostname = hostname;
        this.environment = environment;
        this.region = region;
        this.status = status;
        this.assignedAt = assignedAt;
        this.stopRequestedAt = stopRequestedAt;
        this.endedAt = endedAt;
    }

    public UUID runId() {
        return runId;
    }

    public RunAssignmentStatus status() {
        return status;
    }

    public void markStopped(Instant now) {
        status = RunAssignmentStatus.STOPPED;
        stopRequestedAt = now;
        endedAt = now;
    }

    public RunAssignment toDomain() {
        return new RunAssignment(
                id,
                runId,
                agentId,
                agentName,
                hostname,
                environment,
                region,
                status,
                assignedAt,
                stopRequestedAt,
                endedAt
        );
    }
}
