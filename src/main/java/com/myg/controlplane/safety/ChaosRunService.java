package com.myg.controlplane.safety;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChaosRunService {

    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final SafetyGuardrailsService safetyGuardrailsService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ChaosRunService(ChaosRunJpaRepository chaosRunJpaRepository,
                           SafetyGuardrailsService safetyGuardrailsService,
                           AuditLogService auditLogService,
                           Clock clock) {
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.safetyGuardrailsService = safetyGuardrailsService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public DispatchAuthorizationResponse createRun(String requestedBy, RunDispatchRequest request) {
        DispatchAuthorizationResponse authorization = safetyGuardrailsService.authorize(requestedBy, request);
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
                requestedBy.trim(),
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

    @Transactional
    public ChaosRunResponse stopRun(UUID runId, String operator, String reason) {
        ChaosRunEntity entity = chaosRunJpaRepository.findById(runId)
                .orElseThrow(() -> new ChaosRunNotFoundException(runId));
        boolean wasActive = entity.toDomain().status() == ChaosRunStatus.ACTIVE;
        entity.markStopRequested(operator.trim(), reason.trim(), clock.instant());
        chaosRunJpaRepository.save(entity);

        ChaosRun run = entity.toDomain();
        if (wasActive && run.status() == ChaosRunStatus.STOP_REQUESTED && run.stopCommandIssuedAt() != null) {
            auditLogService.record(
                    SafetyAuditEventType.RUN_STOP_REQUESTED,
                    AuditResourceType.RUN,
                    run.id().toString(),
                    run.stopCommandIssuedBy(),
                    run.stopCommandReason(),
                    runStopMetadata(run),
                    run.stopCommandIssuedAt()
            );
        }
        return ChaosRunResponse.from(run);
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

    private Map<String, Object> runStopMetadata(ChaosRun run) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", run.targetEnvironment());
        metadata.put("targetSelector", run.targetSelector());
        metadata.put("faultType", run.faultType());
        metadata.put("requestedDurationSeconds", run.requestedDurationSeconds());
        if (run.approvalId() != null) {
            metadata.put("approvalId", run.approvalId().toString());
        }
        metadata.put("stopRequestedAt", run.stopCommandIssuedAt());
        return metadata;
    }
}
