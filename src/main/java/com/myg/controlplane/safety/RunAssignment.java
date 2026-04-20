package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record RunAssignment(
        UUID id,
        UUID runId,
        UUID agentId,
        String agentName,
        String hostname,
        String environment,
        String region,
        RunAssignmentStatus status,
        Instant assignedAt,
        Instant stopRequestedAt,
        Instant endedAt
) {
}
