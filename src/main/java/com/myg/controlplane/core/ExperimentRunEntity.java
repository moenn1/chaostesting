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
@Table(name = "experiment_runs")
public class ExperimentRunEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID experimentId;

    private UUID leadAgentId;

    private UUID approvalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperimentRunStatus status;

    @Column(nullable = false)
    private String initiatedBy;

    @Column(nullable = false, length = 128)
    private String environment;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    @Column(length = 512)
    private String summary;

    protected ExperimentRunEntity() {
    }

    public ExperimentRunEntity(UUID id,
                               UUID experimentId,
                               UUID leadAgentId,
                               UUID approvalId,
                               ExperimentRunStatus status,
                               String initiatedBy,
                               String environment,
                               Instant createdAt,
                               Instant startedAt,
                               Instant completedAt,
                               String summary) {
        this.id = id;
        this.experimentId = experimentId;
        this.leadAgentId = leadAgentId;
        this.approvalId = approvalId;
        this.status = status;
        this.initiatedBy = initiatedBy;
        this.environment = environment;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.summary = summary;
    }
}
