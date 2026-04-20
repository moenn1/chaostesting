package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record DispatchApproval(
        UUID id,
        String targetEnvironment,
        String approvedBy,
        String reason,
        Instant approvedAt,
        Instant expiresAt
) {
}
