package com.myg.controlplane.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchApprovalService {

    private final Clock clock;
    private final SafetyGuardrailsProperties properties;
    private final DispatchApprovalJpaRepository repository;
    private final AuditLogService auditLogService;

    public DispatchApprovalService(Clock clock,
                                   SafetyGuardrailsProperties properties,
                                   DispatchApprovalJpaRepository repository,
                                   AuditLogService auditLogService) {
        this.clock = clock;
        this.properties = properties;
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DispatchApproval createApproval(DispatchApprovalRequest request) {
        Instant now = clock.instant();
        DispatchApprovalEntity entity = new DispatchApprovalEntity(
                UUID.randomUUID(),
                properties.normalizeEnvironment(request.targetEnvironment()),
                request.approvedBy().trim(),
                request.reason().trim(),
                now,
                now.plus(properties.getApprovalTtl())
        );
        DispatchApproval approval = repository.save(entity).toDomain();
        auditLogService.record(
                SafetyAuditEventType.APPROVAL_CREATED,
                AuditResourceType.APPROVAL,
                approval.id().toString(),
                approval.approvedBy(),
                approval.reason(),
                approvalMetadata(approval),
                approval.approvedAt()
        );
        return approval;
    }

    @Transactional(readOnly = true)
    public boolean isActiveFor(UUID approvalId, String targetEnvironment) {
        if (approvalId == null) {
            return false;
        }

        String normalizedEnvironment = properties.normalizeEnvironment(targetEnvironment);
        Instant now = clock.instant();
        return repository.findById(approvalId)
                .map(DispatchApprovalEntity::toDomain)
                .filter(approval -> approval.targetEnvironment().equals(normalizedEnvironment))
                .filter(approval -> !now.isAfter(approval.expiresAt()))
                .isPresent();
    }

    private Map<String, Object> approvalMetadata(DispatchApproval approval) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", approval.targetEnvironment());
        metadata.put("approvedAt", approval.approvedAt());
        metadata.put("expiresAt", approval.expiresAt());
        return metadata;
    }
}
