package com.myg.controlplane.safety;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record SafetyAuditRecord(
        UUID id,
        SafetyAuditEventType action,
        AuditResourceType resourceType,
        String resourceId,
        String actor,
        String summary,
        JsonNode metadata,
        Instant recordedAt
) {
}
