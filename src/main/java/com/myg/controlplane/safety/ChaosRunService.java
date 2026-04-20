package com.myg.controlplane.safety;

import com.myg.controlplane.agents.domain.AgentStatus;
import com.myg.controlplane.agents.service.AgentRegistryService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
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
    private final RunAssignmentJpaRepository runAssignmentJpaRepository;
    private final AgentRegistryService agentRegistryService;
    private final SafetyGuardrailsService safetyGuardrailsService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ChaosRunService(ChaosRunJpaRepository chaosRunJpaRepository,
                           RunAssignmentJpaRepository runAssignmentJpaRepository,
                           AgentRegistryService agentRegistryService,
                           SafetyGuardrailsService safetyGuardrailsService,
                           AuditLogService auditLogService,
                           Clock clock) {
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.runAssignmentJpaRepository = runAssignmentJpaRepository;
        this.agentRegistryService = agentRegistryService;
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
                null,
                null
        );
        ChaosRun run = chaosRunJpaRepository.save(entity).toDomain();
        List<RunAssignmentEntity> assignments = createAssignments(run, authorization.authorizedAt());
        auditLogService.record(
                SafetyAuditEventType.RUN_STARTED,
                AuditResourceType.RUN,
                authorization.dispatchId().toString(),
                requestedBy.trim(),
                "Authorized chaos run for " + authorization.targetSelector(),
                runMetadata(run, assignments),
                authorization.authorizedAt()
        );
        return authorization;
    }

    @Transactional(readOnly = true)
    public List<ChaosRunResponse> findAll(Optional<ChaosRunStatus> status) {
        List<ChaosRunEntity> entities = status
                .map(chaosRunJpaRepository::findAllByStatusOrderByCreatedAtDescIdDesc)
                .orElseGet(chaosRunJpaRepository::findAllByOrderByCreatedAtDescIdDesc);
        List<ChaosRun> domainRuns = entities.stream()
                .map(ChaosRunEntity::toDomain)
                .toList();
        Map<UUID, RunAssignmentSummary> assignmentSummaries = assignmentSummaries(
                domainRuns.stream().map(ChaosRun::id).toList()
        );
        return domainRuns.stream()
                .map(run -> ChaosRunResponse.from(run, assignmentSummaries.getOrDefault(run.id(), RunAssignmentSummary.empty())))
                .toList();
    }

    @Transactional
    public ChaosRunResponse stopRun(UUID runId, String operator, String reason) {
        ChaosRunEntity entity = chaosRunJpaRepository.findById(runId)
                .orElseThrow(() -> new ChaosRunNotFoundException(runId));
        if (!entity.canBeStopped()) {
            throw new RunStopRejectedException(stopRejected(runId, entity.toDomain().status()));
        }
        Instant now = clock.instant();
        String normalizedOperator = operator.trim();
        String normalizedReason = reason.trim();
        entity.markStopped(normalizedOperator, normalizedReason, now);
        chaosRunJpaRepository.save(entity);
        List<RunAssignmentEntity> assignments = stopAssignments(runId, now);

        ChaosRun run = entity.toDomain();
        auditLogService.record(
                SafetyAuditEventType.RUN_STOP_REQUESTED,
                AuditResourceType.RUN,
                run.id().toString(),
                run.stopCommandIssuedBy(),
                run.stopCommandReason(),
                runStopMetadata(run, assignments),
                run.stopCommandIssuedAt()
        );
        return ChaosRunResponse.from(run, assignmentSummary(assignments));
    }

    List<RunAssignmentEntity> stopAssignments(UUID runId, Instant stoppedAt) {
        List<RunAssignmentEntity> assignments = runAssignmentJpaRepository.findAllByRunId(runId);
        assignments.forEach(assignment -> assignment.markStopped(stoppedAt));
        return runAssignmentJpaRepository.saveAll(assignments);
    }

    private List<RunAssignmentEntity> createAssignments(ChaosRun run, Instant assignedAt) {
        List<RunAssignmentEntity> assignments = agentRegistryService.findAll(
                        Optional.of(run.targetEnvironment()),
                        Optional.empty(),
                        Optional.of(AgentStatus.HEALTHY),
                        Optional.of(run.faultType())
                ).stream()
                .map(agent -> new RunAssignmentEntity(
                        UUID.randomUUID(),
                        run.id(),
                        agent.id(),
                        agent.name(),
                        agent.hostname(),
                        agent.environment(),
                        agent.region(),
                        RunAssignmentStatus.ACTIVE,
                        assignedAt,
                        null,
                        null
                ))
                .toList();
        return runAssignmentJpaRepository.saveAll(assignments);
    }

    private Map<String, Object> runMetadata(ChaosRun run, List<RunAssignmentEntity> assignments) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", run.targetEnvironment());
        metadata.put("targetSelector", run.targetSelector());
        metadata.put("faultType", run.faultType());
        metadata.put("requestedDurationSeconds", run.requestedDurationSeconds());
        if (run.approvalId() != null) {
            metadata.put("approvalId", run.approvalId().toString());
        }
        metadata.put("authorizedAt", run.createdAt());
        metadata.put("assignmentCount", assignments.size());
        metadata.put("assignedAgentIds", assignments.stream()
                .map(RunAssignmentEntity::toDomain)
                .map(RunAssignment::agentId)
                .map(UUID::toString)
                .toList());
        return metadata;
    }

    private Map<String, Object> runStopMetadata(ChaosRun run, List<RunAssignmentEntity> assignments) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", run.targetEnvironment());
        metadata.put("targetSelector", run.targetSelector());
        metadata.put("faultType", run.faultType());
        metadata.put("requestedDurationSeconds", run.requestedDurationSeconds());
        if (run.approvalId() != null) {
            metadata.put("approvalId", run.approvalId().toString());
        }
        metadata.put("stopRequestedAt", run.stopCommandIssuedAt());
        metadata.put("endedAt", run.endedAt());
        metadata.put("finalStatus", run.status());
        metadata.put("assignmentCount", assignments.size());
        metadata.put("stoppedAssignmentCount", assignments.size());
        return metadata;
    }

    private Map<UUID, RunAssignmentSummary> assignmentSummaries(Collection<UUID> runIds) {
        if (runIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, RunAssignmentSummary> summaries = new HashMap<>();
        for (RunAssignmentEntity assignment : runAssignmentJpaRepository.findAllByRunIdIn(runIds)) {
            RunAssignmentSummary current = summaries.getOrDefault(assignment.runId(), RunAssignmentSummary.empty());
            long activeCount = current.activeAssignmentCount()
                    + (assignment.status() == RunAssignmentStatus.ACTIVE ? 1 : 0);
            long stoppedCount = current.stoppedAssignmentCount()
                    + (assignment.status() == RunAssignmentStatus.STOPPED ? 1 : 0);
            summaries.put(
                    assignment.runId(),
                    new RunAssignmentSummary(current.assignmentCount() + 1, activeCount, stoppedCount)
            );
        }
        return summaries;
    }

    private RunAssignmentSummary assignmentSummary(List<RunAssignmentEntity> assignments) {
        long activeCount = assignments.stream().filter(assignment -> assignment.status() == RunAssignmentStatus.ACTIVE).count();
        long stoppedCount = assignments.stream().filter(assignment -> assignment.status() == RunAssignmentStatus.STOPPED).count();
        return new RunAssignmentSummary(assignments.size(), activeCount, stoppedCount);
    }

    private RunStopValidationResponse stopRejected(UUID runId, ChaosRunStatus currentStatus) {
        String code = switch (currentStatus) {
            case STOP_REQUESTED -> "RUN_STOP_ALREADY_REQUESTED";
            case STOPPED -> "RUN_ALREADY_STOPPED";
            case COMPLETED -> "RUN_ALREADY_COMPLETED";
            case ACTIVE -> "RUN_STOP_NOT_ALLOWED";
        };
        String message = switch (currentStatus) {
            case STOP_REQUESTED -> "Run already has a pending stop request.";
            case STOPPED -> "Run has already been stopped.";
            case COMPLETED -> "Completed runs cannot be stopped again.";
            case ACTIVE -> "Run stop is only allowed for active runs.";
        };
        return new RunStopValidationResponse(code, message, runId, currentStatus, List.of(ChaosRunStatus.ACTIVE));
    }
}
