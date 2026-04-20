package com.myg.controlplane.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dispatch_approvals")
public class DispatchApprovalEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String targetEnvironment;

    @Column(nullable = false)
    private String approvedBy;

    @Column(nullable = false, length = 512)
    private String reason;

    @Column(nullable = false)
    private Instant approvedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected DispatchApprovalEntity() {
    }

    public DispatchApprovalEntity(UUID id,
                                  String targetEnvironment,
                                  String approvedBy,
                                  String reason,
                                  Instant approvedAt,
                                  Instant expiresAt) {
        this.id = id;
        this.targetEnvironment = targetEnvironment;
        this.approvedBy = approvedBy;
        this.reason = reason;
        this.approvedAt = approvedAt;
        this.expiresAt = expiresAt;
    }

    public DispatchApproval toDomain() {
        return new DispatchApproval(id, targetEnvironment, approvedBy, reason, approvedAt, expiresAt);
    }
}
