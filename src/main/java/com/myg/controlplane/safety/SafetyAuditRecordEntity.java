package com.myg.controlplane.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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

    protected SafetyAuditRecordEntity() {
    }

    public SafetyAuditRecordEntity(UUID id,
                                   SafetyAuditEventType eventType,
                                   UUID runId,
                                   String operator,
                                   String reason,
                                   Instant recordedAt) {
        this.id = id;
        this.eventType = eventType;
        this.runId = runId;
        this.operator = operator;
        this.reason = reason;
        this.recordedAt = recordedAt;
    }

    public SafetyAuditRecord toDomain() {
        return new SafetyAuditRecord(id, eventType, runId, operator, reason, recordedAt);
    }
}
