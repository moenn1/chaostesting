package com.myg.controlplane.safety;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record SafetyAuditRecordResponse(
        UUID id,
        SafetyAuditEventType action,
        AuditResourceType resourceType,
        String resourceId,
        String actor,
        String summary,
        JsonNode metadata,
        Instant recordedAt
) {
    public static SafetyAuditRecordResponse from(SafetyAuditRecord record) {
        return new SafetyAuditRecordResponse(
                record.id(),
                record.action(),
                record.resourceType(),
                record.resourceId(),
                record.actor(),
                record.summary(),
                record.metadata(),
                record.recordedAt()
        );
    }
}
