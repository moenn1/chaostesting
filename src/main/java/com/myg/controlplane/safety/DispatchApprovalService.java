package com.myg.controlplane.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchApprovalService {

    private final Clock clock;
    private final SafetyGuardrailsProperties properties;
    private final DispatchApprovalJpaRepository repository;

    public DispatchApprovalService(Clock clock,
                                   SafetyGuardrailsProperties properties,
                                   DispatchApprovalJpaRepository repository) {
        this.clock = clock;
        this.properties = properties;
        this.repository = repository;
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
        return repository.save(entity).toDomain();
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
}
