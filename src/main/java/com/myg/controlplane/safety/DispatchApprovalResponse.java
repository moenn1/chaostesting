package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record DispatchApprovalResponse(
        UUID id,
        String targetEnvironment,
        String approvedBy,
        String reason,
        Instant approvedAt,
        Instant expiresAt
) {
    public static DispatchApprovalResponse from(DispatchApproval approval) {
        return new DispatchApprovalResponse(
                approval.id(),
                approval.targetEnvironment(),
                approval.approvedBy(),
                approval.reason(),
                approval.approvedAt(),
                approval.expiresAt()
        );
    }
}
