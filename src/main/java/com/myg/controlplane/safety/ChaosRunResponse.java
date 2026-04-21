package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChaosRunResponse(
        UUID id,
        UUID experimentId,
        ChaosRunStatus status,
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
        Instant createdAt,
        Instant endedAt,
        Instant rollbackScheduledAt,
        Instant rollbackVerifiedAt,
        Instant startedAt,
        RunTargetSnapshot targetSnapshot,
        Instant stopCommandIssuedAt,
        String stopCommandIssuedBy,
        String stopCommandReason,
        String statusMessage,
        long assignmentCount,
        long activeAssignmentCount,
        long stoppedAssignmentCount
) {
    public ChaosRunResponse {
        routeFilters = routeFilters == null ? List.of() : List.copyOf(routeFilters);
    }

    public static ChaosRunResponse from(ChaosRun run) {
        return from(run, inferAssignmentSummary(run));
    }

    public static ChaosRunResponse from(ChaosRun run, RunAssignmentSummary assignmentSummary) {
        return new ChaosRunResponse(
                run.id(),
                run.experimentId(),
                run.status(),
                run.targetEnvironment(),
                run.targetSelector(),
                run.faultType(),
                run.requestedDurationSeconds(),
                run.latencyMilliseconds(),
                run.latencyJitterMilliseconds(),
                run.latencyMinimumMilliseconds(),
                run.latencyMaximumMilliseconds(),
                run.errorCode(),
                run.trafficPercentage(),
                run.dropPercentage(),
                run.routeFilters(),
                run.approvalId(),
                run.createdAt(),
                run.endedAt(),
                run.rollbackScheduledAt(),
                run.rollbackVerifiedAt(),
                run.startedAt(),
                run.targetSnapshot(),
                run.stopCommandIssuedAt(),
                run.stopCommandIssuedBy(),
                run.stopCommandReason(),
                RunStatusMessages.currentStatusMessage(run),
                assignmentSummary.assignmentCount(),
                assignmentSummary.activeAssignmentCount(),
                assignmentSummary.stoppedAssignmentCount()
        );
    }

    private static RunAssignmentSummary inferAssignmentSummary(ChaosRun run) {
        int assignmentCount = run.targetSnapshot() == null ? 0 : run.targetSnapshot().assignedAgents().size();
        if (assignmentCount == 0) {
            return RunAssignmentSummary.empty();
        }

        return switch (run.status()) {
            case ACTIVE, STOP_REQUESTED -> new RunAssignmentSummary(assignmentCount, assignmentCount, 0);
            case FAILED, ROLLED_BACK, STOPPED, COMPLETED -> new RunAssignmentSummary(assignmentCount, 0, assignmentCount);
        };
    }
}
