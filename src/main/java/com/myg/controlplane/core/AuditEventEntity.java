package com.myg.controlplane.core;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

    @Id
    private UUID id;

    private UUID experimentId;

    private UUID experimentRunId;

    private UUID agentId;

    private UUID faultInjectionActionId;

    @Column(nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 1024)
    private String message;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditEventEntity() {
    }

    public AuditEventEntity(UUID id,
                            UUID experimentId,
                            UUID experimentRunId,
                            UUID agentId,
                            UUID faultInjectionActionId,
                            String eventType,
                            String actor,
                            String message,
                            String metadataJson,
                            Instant occurredAt) {
        this.id = id;
        this.experimentId = experimentId;
        this.experimentRunId = experimentRunId;
        this.agentId = agentId;
        this.faultInjectionActionId = faultInjectionActionId;
        this.eventType = eventType;
        this.actor = actor;
        this.message = message;
        this.metadataJson = metadataJson;
        this.occurredAt = occurredAt;
    }
}
