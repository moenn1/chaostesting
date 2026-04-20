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
@Table(name = "run_execution_reports")
public class RunExecutionReportEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunExecutionReportState state;

    @Column(nullable = false)
    private String reportedBy;

    @Column(nullable = false, length = 512)
    private String message;

    @Column(nullable = false)
    private Instant reportedAt;

    protected RunExecutionReportEntity() {
    }

    public RunExecutionReportEntity(UUID id,
                                    UUID runId,
                                    RunExecutionReportState state,
                                    String reportedBy,
                                    String message,
                                    Instant reportedAt) {
        this.id = id;
        this.runId = runId;
        this.state = state;
        this.reportedBy = reportedBy;
        this.message = message;
        this.reportedAt = reportedAt;
    }

    public RunExecutionReport toDomain() {
        return new RunExecutionReport(id, runId, state, reportedBy, message, reportedAt);
    }
}
