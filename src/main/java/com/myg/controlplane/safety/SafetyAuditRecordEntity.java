package com.myg.controlplane.safety;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_audit_records")
public class SafetyAuditRecordEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SafetyAuditEventType eventType;

    private UUID runId;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(nullable = false)
    private Instant recordedAt;

    @Enumerated(EnumType.STRING)
    private AuditResourceType resourceType;

    private String resourceId;

    @Lob
    private String metadataJson;

    protected SafetyAuditRecordEntity() {
    }

    public SafetyAuditRecordEntity(UUID id,
                                   SafetyAuditEventType eventType,
                                   UUID runId,
                                   String operator,
                                   String reason,
                                   Instant recordedAt,
                                   AuditResourceType resourceType,
                                   String resourceId,
                                   String metadataJson) {
        this.id = id;
        this.eventType = eventType;
        this.runId = runId;
        this.operator = operator;
        this.reason = reason;
        this.recordedAt = recordedAt;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.metadataJson = metadataJson;
    }

    public SafetyAuditRecord toDomain(ObjectMapper objectMapper) {
        return new SafetyAuditRecord(
                id,
                eventType,
                resolvedResourceType(),
                resolvedResourceId(),
                operator,
                reason,
                parseMetadata(objectMapper),
                recordedAt
        );
    }

    private AuditResourceType resolvedResourceType() {
        if (resourceType != null) {
            return resourceType;
        }
        return runId != null ? AuditResourceType.RUN : AuditResourceType.KILL_SWITCH;
    }

    private String resolvedResourceId() {
        if (resourceId != null && !resourceId.isBlank()) {
            return resourceId;
        }
        if (runId != null) {
            return runId.toString();
        }
        return "global";
    }

    private ObjectNode parseMetadata(ObjectMapper objectMapper) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return (ObjectNode) objectMapper.readTree(metadataJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize audit metadata", exception);
        }
    }
}
