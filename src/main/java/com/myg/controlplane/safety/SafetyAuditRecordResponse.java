package com.myg.controlplane.safety;

import java.time.Instant;
import java.util.UUID;

public record SafetyAuditRecordResponse(
        UUID id,
        SafetyAuditEventType eventType,
        UUID runId,
        String operator,
        String reason,
        Instant recordedAt
) {
    public static SafetyAuditRecordResponse from(SafetyAuditRecord record) {
        return new SafetyAuditRecordResponse(
                record.id(),
                record.eventType(),
                record.runId(),
                record.operator(),
                record.reason(),
                record.recordedAt()
        );
    }
}
