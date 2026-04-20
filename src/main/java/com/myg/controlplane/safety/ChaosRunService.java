package com.myg.controlplane.safety;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChaosRunService {

    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final SafetyGuardrailsService safetyGuardrailsService;
    private final AuditLogService auditLogService;

    public ChaosRunService(ChaosRunJpaRepository chaosRunJpaRepository,
                           SafetyGuardrailsService safetyGuardrailsService,
                           AuditLogService auditLogService) {
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.safetyGuardrailsService = safetyGuardrailsService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public DispatchAuthorizationResponse createRun(RunDispatchRequest request) {
        DispatchAuthorizationResponse authorization = safetyGuardrailsService.authorize(request);
        ChaosRunEntity entity = new ChaosRunEntity(
                authorization.dispatchId(),
                authorization.targetEnvironment(),
                authorization.targetSelector(),
                authorization.faultType(),
                authorization.requestedDurationSeconds(),
                authorization.approvalId(),
                ChaosRunStatus.ACTIVE,
                authorization.authorizedAt(),
                null,
                null,
                null
        );
        chaosRunJpaRepository.save(entity);
        auditLogService.record(
                SafetyAuditEventType.RUN_STARTED,
                AuditResourceType.RUN,
                authorization.dispatchId().toString(),
                request.requestedBy().trim(),
                "Authorized chaos run for " + authorization.targetSelector(),
                runMetadata(authorization),
                authorization.authorizedAt()
        );
        return authorization;
    }

    @Transactional(readOnly = true)
    public List<ChaosRunResponse> findAll(Optional<ChaosRunStatus> status) {
        List<ChaosRunEntity> entities = status
                .map(chaosRunJpaRepository::findAllByStatusOrderByCreatedAtDescIdDesc)
                .orElseGet(chaosRunJpaRepository::findAllByOrderByCreatedAtDescIdDesc);
        return entities.stream()
                .map(ChaosRunEntity::toDomain)
                .map(ChaosRunResponse::from)
                .toList();
    }

    private Map<String, Object> runMetadata(DispatchAuthorizationResponse authorization) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", authorization.targetEnvironment());
        metadata.put("targetSelector", authorization.targetSelector());
        metadata.put("faultType", authorization.faultType());
        metadata.put("requestedDurationSeconds", authorization.requestedDurationSeconds());
        if (authorization.approvalId() != null) {
            metadata.put("approvalId", authorization.approvalId().toString());
        }
        metadata.put("authorizedAt", authorization.authorizedAt());
        return metadata;
    }
}
