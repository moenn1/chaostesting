package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record SafetyAuditRecord(
        UUID id,
        SafetyAuditEventType eventType,
        UUID runId,
        String operator,
        String reason,
        Instant recordedAt
) {
}
