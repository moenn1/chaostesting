package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record RunExecutionReportResponse(
        UUID id,
        UUID runId,
        RunExecutionReportState state,
        String reportedBy,
        String message,
        Instant reportedAt
) {
    public static RunExecutionReportResponse from(RunExecutionReport report) {
        return new RunExecutionReportResponse(
                report.id(),
                report.runId(),
                report.state(),
                report.reportedBy(),
                report.message(),
                report.reportedAt()
        );
    }
}
