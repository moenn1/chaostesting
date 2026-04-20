package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record RunExecutionReport(
        UUID id,
        UUID runId,
        RunExecutionReportState state,
        String reportedBy,
        String message,
        Instant reportedAt
) {
}
