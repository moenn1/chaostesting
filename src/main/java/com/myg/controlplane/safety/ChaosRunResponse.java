package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record ChaosRunResponse(
        UUID id,
        ChaosRunStatus status,
        String targetEnvironment,
        String targetSelector,
        String faultType,
        long requestedDurationSeconds,
        UUID approvalId,
        Instant createdAt,
        Instant endedAt,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason,
        long assignmentCount,
        long activeAssignmentCount,
        long stoppedAssignmentCount
) {
    public static ChaosRunResponse from(ChaosRun run, RunAssignmentSummary assignmentSummary) {
        return new ChaosRunResponse(
                run.id(),
                run.status(),
                run.targetEnvironment(),
                run.targetSelector(),
                run.faultType(),
                run.requestedDurationSeconds(),
                run.approvalId(),
                run.createdAt(),
                run.endedAt(),
                run.stopCommandIssuedAt(),
                run.stopCommandIssuedBy(),
                run.stopCommandReason(),
                assignmentSummary.assignmentCount(),
                assignmentSummary.activeAssignmentCount(),
                assignmentSummary.stoppedAssignmentCount()
        );
    }
}
