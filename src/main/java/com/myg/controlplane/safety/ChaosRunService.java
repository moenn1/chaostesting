package com.myg.controlplane.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myg.controlplane.experiments.Experiment;
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
    private final RunLifecycleEventService runLifecycleEventService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ChaosRunService(ChaosRunJpaRepository chaosRunJpaRepository,
                           SafetyGuardrailsService safetyGuardrailsService,
                           AuditLogService auditLogService,
                           RunLifecycleEventService runLifecycleEventService,
                           ObjectMapper objectMapper,
                           Clock clock) {
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.safetyGuardrailsService = safetyGuardrailsService;
        this.auditLogService = auditLogService;
        this.runLifecycleEventService = runLifecycleEventService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public DispatchAuthorizationResponse createRun(String requestedBy, RunDispatchRequest request) {
        DispatchAuthorizationResponse authorization = safetyGuardrailsService.authorize(requestedBy, request);
        ChaosRun run = persistRun(authorization, null, null);
        auditLogService.record(
                SafetyAuditEventType.RUN_STARTED,
                AuditResourceType.RUN,
                run.id().toString(),
                requestedBy.trim(),
                "Authorized chaos run for " + run.targetSelector(),
                runMetadata(run),
                run.startedAt()
        );
        return authorization;
    }

    @Transactional
    public ChaosRun createManualRun(String requestedBy,
                                    Experiment experiment,
                                    UUID approvalId,
                                    RunTargetSnapshot targetSnapshot,
                                    String targetSelectorDescription) {
        RunDispatchRequest request = new RunDispatchRequest(
                experiment.environmentMetadata().environment(),
                targetSelectorDescription,
                experiment.faultConfig().type(),
                experiment.faultConfig().durationSeconds(),
                approvalId
        );
        DispatchAuthorizationResponse authorization = safetyGuardrailsService.authorize(requestedBy, request);
        ChaosRun run = persistRun(authorization, experiment.id(), targetSnapshot);
        auditLogService.record(
                SafetyAuditEventType.RUN_STARTED,
                AuditResourceType.RUN,
                run.id().toString(),
                requestedBy.trim(),
                "Started manual run for experiment " + experiment.name(),
                runMetadata(run),
                run.startedAt()
        );
        runLifecycleEventService.recordRunStarted(run, requestedBy.trim(), experiment.name());
        return run;
    }

    @Transactional(readOnly = true)
    public List<ChaosRunResponse> findAll(Optional<ChaosRunStatus> status) {
        List<ChaosRunEntity> entities = status
                .map(chaosRunJpaRepository::findAllByStatusOrderByCreatedAtDescIdDesc)
                .orElseGet(chaosRunJpaRepository::findAllByOrderByCreatedAtDescIdDesc);
        return entities.stream()
                .map(entity -> entity.toDomain(objectMapper))
                .map(ChaosRunResponse::from)
                .toList();
    }

    @Transactional
    public ChaosRunResponse stopRun(UUID runId, String operator, String reason) {
        ChaosRunEntity entity = chaosRunJpaRepository.findById(runId)
                .orElseThrow(() -> new ChaosRunNotFoundException(runId));
        boolean wasActive = entity.toDomain(objectMapper).status() == ChaosRunStatus.ACTIVE;
        entity.markStopRequested(operator.trim(), reason.trim(), clock.instant());
        chaosRunJpaRepository.save(entity);

        ChaosRun run = entity.toDomain(objectMapper);
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

    private ChaosRun persistRun(DispatchAuthorizationResponse authorization,
                                UUID experimentId,
                                RunTargetSnapshot targetSnapshot) {
        ChaosRunEntity entity = new ChaosRunEntity(
                authorization.dispatchId(),
                experimentId,
                authorization.targetEnvironment(),
                authorization.targetSelector(),
                authorization.faultType(),
                authorization.requestedDurationSeconds(),
                authorization.approvalId(),
                ChaosRunStatus.ACTIVE,
                authorization.authorizedAt(),
                authorization.authorizedAt(),
                ChaosRunEntity.writeJson(objectMapper, targetSnapshot),
                null,
                null,
                null
        );
        return chaosRunJpaRepository.save(entity).toDomain(objectMapper);
    }

    private Map<String, Object> runMetadata(ChaosRun run) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", run.targetEnvironment());
        metadata.put("targetSelector", run.targetSelector());
        metadata.put("faultType", run.faultType());
        metadata.put("requestedDurationSeconds", run.requestedDurationSeconds());
        metadata.put("startedAt", run.startedAt());
        if (run.experimentId() != null) {
            metadata.put("experimentId", run.experimentId().toString());
        }
        if (run.approvalId() != null) {
            metadata.put("approvalId", run.approvalId().toString());
        }
        if (run.targetSnapshot() != null) {
            metadata.put("services", run.targetSnapshot().services());
            metadata.put("assignedAgents", run.targetSnapshot().assignedAgents().stream()
                    .map(RunAssignedAgent::id)
                    .map(UUID::toString)
                    .toList());
        }
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
