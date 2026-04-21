package com.myg.controlplane.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myg.controlplane.agents.domain.AgentStatus;
import com.myg.controlplane.agents.service.AgentRegistryService;
import com.myg.controlplane.experiments.Experiment;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChaosRunService {

    private static final String SYSTEM_OPERATOR = "system";
    private static final String TIMEBOX_COMPLETED_REASON = "requested duration elapsed";

    private final Clock clock;
    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final RunAssignmentJpaRepository runAssignmentJpaRepository;
    private final AgentRegistryService agentRegistryService;
    private final LatencyTelemetrySnapshotJpaRepository latencyTelemetrySnapshotJpaRepository;
    private final RunExecutionReportJpaRepository runExecutionReportJpaRepository;
    private final AuditLogService auditLogService;
    private final RunLifecycleEventService runLifecycleEventService;
    private final ObjectMapper objectMapper;
    private final TaskScheduler taskScheduler;
    private final LatencyInjectionProperties latencyInjectionProperties;
    private final ConcurrentMap<UUID, RunScheduleHandle> runtimeSchedules = new ConcurrentHashMap<>();

    public ChaosRunService(Clock clock,
                           ChaosRunJpaRepository chaosRunJpaRepository,
                           RunAssignmentJpaRepository runAssignmentJpaRepository,
                           AgentRegistryService agentRegistryService,
                           LatencyTelemetrySnapshotJpaRepository latencyTelemetrySnapshotJpaRepository,
                           RunExecutionReportJpaRepository runExecutionReportJpaRepository,
                           AuditLogService auditLogService,
                           RunLifecycleEventService runLifecycleEventService,
                           ObjectMapper objectMapper,
                           TaskScheduler taskScheduler,
                           LatencyInjectionProperties latencyInjectionProperties) {
        this.clock = clock;
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.runAssignmentJpaRepository = runAssignmentJpaRepository;
        this.agentRegistryService = agentRegistryService;
        this.latencyTelemetrySnapshotJpaRepository = latencyTelemetrySnapshotJpaRepository;
        this.runExecutionReportJpaRepository = runExecutionReportJpaRepository;
        this.auditLogService = auditLogService;
        this.runLifecycleEventService = runLifecycleEventService;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
        this.latencyInjectionProperties = latencyInjectionProperties;
    }

    @Transactional
    public void startAuthorizedRun(DispatchAuthorizationResponse authorization, RunDispatchRequest request) {
        ChaosRun run = persistRun(authorization, null, null);
        List<RunAssignmentEntity> assignments = createAssignments(run, run.startedAt());
        if (supportsTelemetrySnapshots(run)) {
            latencyTelemetrySnapshotJpaRepository.save(LatencyTelemetrySnapshotEntity.injection(
                    run,
                    authorization.authorizedAt(),
                    RunStatusMessages.activationMessage(run)
            ));
        }
        auditLogService.record(
                SafetyAuditEventType.RUN_STARTED,
                AuditResourceType.RUN,
                run.id().toString(),
                request.requestedBy().trim(),
                "Authorized chaos run for " + authorization.targetSelector(),
                runMetadata(run, authorization.authorizedAt(), assignmentSummary(assignments)),
                authorization.authorizedAt()
        );
        scheduleRuntime(run, authorization.authorizedAt());
    }

    @Transactional
    public ChaosRun createManualRun(String requestedBy,
                                    Experiment experiment,
                                    DispatchAuthorizationResponse authorization,
                                    RunTargetSnapshot targetSnapshot) {
        ChaosRun run = persistRun(authorization, experiment.id(), targetSnapshot);
        List<RunAssignmentEntity> assignments = createAssignments(run, run.startedAt());
        if (supportsTelemetrySnapshots(run)) {
            latencyTelemetrySnapshotJpaRepository.save(LatencyTelemetrySnapshotEntity.injection(
                    run,
                    run.startedAt(),
                    RunStatusMessages.activationMessage(run)
            ));
        }
        auditLogService.record(
                SafetyAuditEventType.RUN_STARTED,
                AuditResourceType.RUN,
                run.id().toString(),
                requestedBy.trim(),
                "Started manual run for experiment " + experiment.name(),
                runMetadata(run, run.startedAt(), assignmentSummary(assignments)),
                run.startedAt()
        );
        runLifecycleEventService.recordRunStarted(run, requestedBy.trim(), experiment.name());
        scheduleRuntime(run, run.startedAt());
        return run;
    }

    @Transactional(readOnly = true)
    public List<ChaosRunResponse> findAll(Optional<ChaosRunStatus> status) {
        List<ChaosRunEntity> entities = status
                .map(chaosRunJpaRepository::findAllByStatusOrderByCreatedAtDescIdDesc)
                .orElseGet(chaosRunJpaRepository::findAllByOrderByCreatedAtDescIdDesc);
        List<ChaosRun> domainRuns = entities.stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
        Map<UUID, RunAssignmentSummary> assignmentSummaries = assignmentSummaries(
                domainRuns.stream().map(ChaosRun::id).toList()
        );
        return domainRuns.stream()
                .map(run -> ChaosRunResponse.from(run, assignmentSummaries.getOrDefault(run.id(), RunAssignmentSummary.empty())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChaosRunResponse getRun(UUID runId) {
        ChaosRun run = loadRun(runId).toDomain(objectMapper);
        return ChaosRunResponse.from(run, assignmentSummary(loadAssignments(runId)));
    }

    @Transactional(readOnly = true)
    public List<LatencyTelemetrySnapshotResponse> findTelemetry(UUID runId) {
        ensureRunExists(runId);
        return latencyTelemetrySnapshotJpaRepository.findAllByRunIdOrderByCapturedAtDescIdDesc(runId).stream()
                .map(LatencyTelemetrySnapshotEntity::toDomain)
                .map(LatencyTelemetrySnapshotResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RunExecutionReportResponse> findReports(UUID runId) {
        ensureRunExists(runId);
        return runExecutionReportJpaRepository.findAllByRunIdOrderByReportedAtDescIdDesc(runId).stream()
                .map(RunExecutionReportEntity::toDomain)
                .map(RunExecutionReportResponse::from)
                .toList();
    }

    @Transactional
    public RunExecutionReportResponse reportRun(UUID runId, String reportedBy, RunExecutionReportRequest request) {
        ChaosRunEntity entity = loadRun(runId);
        Instant now = clock.instant();
        String message = request.message().trim();
        ChaosRun currentRun = entity.toDomain(objectMapper);

        if (request.state() == RunExecutionReportState.FAILURE
                && currentRun.status() != ChaosRunStatus.ROLLED_BACK
                && currentRun.status() != ChaosRunStatus.FAILED) {
            cancelRuntime(runId);
            List<RunAssignmentEntity> stoppedAssignments = stopAssignments(runId, now);
            entity.markFailed(now);
            chaosRunJpaRepository.save(entity);

            ChaosRun failedRun = entity.toDomain(objectMapper);
            auditLogService.record(
                    SafetyAuditEventType.RUN_EXECUTION_FAILED,
                    AuditResourceType.RUN,
                    runId.toString(),
                    reportedBy,
                    "Execution failed for " + failedRun.targetSelector(),
                    failureMetadata(failedRun, message, now, assignmentSummary(stoppedAssignments)),
                    now
            );
        } else if (request.state() == RunExecutionReportState.ROLLBACK
                && currentRun.status() != ChaosRunStatus.ROLLED_BACK) {
            cancelRuntime(runId);
            List<RunAssignmentEntity> stoppedAssignments = stopAssignments(runId, now);
            entity.markRolledBack(now);
            chaosRunJpaRepository.save(entity);

            ChaosRun rolledBackRun = entity.toDomain(objectMapper);
            if (supportsTelemetrySnapshots(rolledBackRun)) {
                latencyTelemetrySnapshotJpaRepository.save(LatencyTelemetrySnapshotEntity.rollback(
                        rolledBackRun,
                        now,
                        RunStatusMessages.rollbackMessage(rolledBackRun, message)
                ));
            }
            auditLogService.record(
                    SafetyAuditEventType.RUN_ROLLBACK_VERIFIED,
                    AuditResourceType.RUN,
                    runId.toString(),
                    reportedBy,
                    "Rollback verified for " + rolledBackRun.targetSelector(),
                    rollbackMetadata(rolledBackRun, now, message, assignmentSummary(stoppedAssignments)),
                    now
            );
        }

        return RunExecutionReportResponse.from(saveReport(runId, request.state(), reportedBy, message, now).toDomain());
    }

    @Transactional
    public ChaosRunResponse stopRun(UUID runId, String operator, String reason) {
        ChaosRunEntity entity = loadRun(runId);
        if (!entity.canBeStopped()) {
            throw new RunStopRejectedException(stopRejected(runId, entity.toDomain(objectMapper).status()));
        }
        rollbackRun(entity, operator.trim(), reason.trim(), clock.instant());
        return ChaosRunResponse.from(entity.toDomain(objectMapper), assignmentSummary(loadAssignments(runId)));
    }

    @Transactional
    public long stopActiveRuns(String operator, String reason) {
        Instant now = clock.instant();
        String normalizedOperator = operator.trim();
        String normalizedReason = reason.trim();
        List<ChaosRunEntity> activeRuns = chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.ACTIVE);
        activeRuns.forEach(run -> rollbackRun(run, normalizedOperator, normalizedReason, now));
        return activeRuns.size();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void restoreRuntimeSchedules() {
        Instant now = clock.instant();
        chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.ACTIVE).forEach(runEntity -> {
            ChaosRun run = runEntity.toDomain(objectMapper);
            if (run.rollbackScheduledAt() == null || !run.rollbackScheduledAt().isAfter(now)) {
                rollbackRun(runEntity, SYSTEM_OPERATOR, TIMEBOX_COMPLETED_REASON, now);
            } else {
                scheduleRuntime(run, now);
            }
        });
        chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.STOP_REQUESTED)
                .forEach(run -> rollbackRun(run, SYSTEM_OPERATOR, "resume pending rollback on startup", now));
    }

    @Transactional
    void emitScheduledTelemetry(UUID runId) {
        chaosRunJpaRepository.findById(runId).ifPresent(runEntity -> {
            ChaosRun run = runEntity.toDomain(objectMapper);
            if (run.status() != ChaosRunStatus.ACTIVE) {
                cancelRuntime(runId);
                return;
            }
            if (supportsPeriodicTelemetry(run)) {
                latencyTelemetrySnapshotJpaRepository.save(LatencyTelemetrySnapshotEntity.injection(
                        run,
                        clock.instant(),
                        RunStatusMessages.activeTelemetryMessage(run)
                ));
            }
        });
    }

    @Transactional
    void rollbackDueRun(UUID runId) {
        chaosRunJpaRepository.findById(runId).ifPresent(runEntity -> {
            if (runEntity.toDomain(objectMapper).status() == ChaosRunStatus.ACTIVE) {
                rollbackRun(runEntity, SYSTEM_OPERATOR, TIMEBOX_COMPLETED_REASON, clock.instant());
            } else {
                cancelRuntime(runId);
            }
        });
    }

    private void scheduleRuntime(ChaosRun run, Instant scheduledFrom) {
        cancelRuntime(run.id());
        if (run.rollbackScheduledAt() == null) {
            return;
        }

        Instant firstTelemetryAt = scheduledFrom.plus(latencyInjectionProperties.getTelemetryInterval());
        ScheduledFuture<?> telemetryFuture = null;
        if (supportsPeriodicTelemetry(run) && firstTelemetryAt.isBefore(run.rollbackScheduledAt())) {
            telemetryFuture = taskScheduler.scheduleAtFixedRate(
                    () -> emitScheduledTelemetry(run.id()),
                    firstTelemetryAt,
                    latencyInjectionProperties.getTelemetryInterval()
            );
        }

        ScheduledFuture<?> rollbackFuture = taskScheduler.schedule(
                () -> rollbackDueRun(run.id()),
                run.rollbackScheduledAt()
        );
        runtimeSchedules.put(run.id(), new RunScheduleHandle(telemetryFuture, rollbackFuture));
    }

    private void rollbackRun(ChaosRunEntity entity, String operator, String reason, Instant now) {
        ChaosRun currentRun = entity.toDomain(objectMapper);
        if (currentRun.status() == ChaosRunStatus.ROLLED_BACK
                || currentRun.status() == ChaosRunStatus.STOPPED
                || currentRun.status() == ChaosRunStatus.COMPLETED
                || currentRun.status() == ChaosRunStatus.FAILED) {
            cancelRuntime(currentRun.id());
            return;
        }

        cancelRuntime(currentRun.id());
        List<RunAssignmentEntity> stoppedAssignments = stopAssignments(currentRun.id(), now);
        RunAssignmentSummary assignmentSummary = assignmentSummary(stoppedAssignments);

        if (currentRun.status() == ChaosRunStatus.ACTIVE) {
            entity.markStopRequested(operator, reason, now);
            auditLogService.record(
                    SafetyAuditEventType.RUN_STOP_REQUESTED,
                    AuditResourceType.RUN,
                    currentRun.id().toString(),
                    operator,
                    "Stop requested for " + currentRun.targetSelector(),
                    runStopMetadata(currentRun, now, assignmentSummary),
                    now
            );
        }

        Instant rollbackVerifiedAt = now.plusMillis(1);
        entity.markRolledBack(rollbackVerifiedAt);
        chaosRunJpaRepository.save(entity);

        ChaosRun rolledBackRun = entity.toDomain(objectMapper);
        saveReport(
                currentRun.id(),
                RunExecutionReportState.ROLLBACK,
                operator,
                "Rollback verified after " + reason + ".",
                rollbackVerifiedAt
        );
        if (supportsTelemetrySnapshots(rolledBackRun)) {
            latencyTelemetrySnapshotJpaRepository.save(LatencyTelemetrySnapshotEntity.rollback(
                    rolledBackRun,
                    rollbackVerifiedAt,
                    RunStatusMessages.rollbackMessage(rolledBackRun, reason)
            ));
        }
        auditLogService.record(
                SafetyAuditEventType.RUN_ROLLBACK_VERIFIED,
                AuditResourceType.RUN,
                rolledBackRun.id().toString(),
                operator,
                "Rollback verified for " + rolledBackRun.targetSelector(),
                rollbackMetadata(rolledBackRun, rollbackVerifiedAt, reason, assignmentSummary),
                rollbackVerifiedAt
        );
    }

    private ChaosRunEntity loadRun(UUID runId) {
        return chaosRunJpaRepository.findById(runId)
                .orElseThrow(() -> new ChaosRunNotFoundException(runId));
    }

    private void ensureRunExists(UUID runId) {
        if (!chaosRunJpaRepository.existsById(runId)) {
            throw new ChaosRunNotFoundException(runId);
        }
    }

    private void cancelRuntime(UUID runId) {
        RunScheduleHandle handle = runtimeSchedules.remove(runId);
        if (handle == null) {
            return;
        }
        handle.cancel();
    }

    private List<RunAssignmentEntity> loadAssignments(UUID runId) {
        return runAssignmentJpaRepository.findAllByRunId(runId);
    }

    List<RunAssignmentEntity> stopAssignments(UUID runId, Instant stoppedAt) {
        List<RunAssignmentEntity> assignments = loadAssignments(runId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        assignments.forEach(assignment -> assignment.markStopped(stoppedAt));
        return runAssignmentJpaRepository.saveAll(assignments);
    }

    private List<RunAssignmentEntity> createAssignments(ChaosRun run, Instant assignedAt) {
        List<RunAssignmentEntity> assignments = resolveAssignedAgents(run).stream()
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
        if (assignments.isEmpty()) {
            return List.of();
        }
        return runAssignmentJpaRepository.saveAll(assignments);
    }

    private List<RunAssignedAgent> resolveAssignedAgents(ChaosRun run) {
        if (run.targetSnapshot() != null && !run.targetSnapshot().assignedAgents().isEmpty()) {
            return run.targetSnapshot().assignedAgents();
        }
        return agentRegistryService.findAll(
                        Optional.of(run.targetEnvironment()),
                        Optional.empty(),
                        Optional.of(AgentStatus.HEALTHY),
                        Optional.of(run.faultType())
                ).stream()
                .map(RunAssignedAgent::from)
                .toList();
    }

    private Map<String, Object> runMetadata(ChaosRun run,
                                            Instant authorizedAt,
                                            RunAssignmentSummary assignmentSummary) {
        Map<String, Object> metadata = baseRunMetadata(run);
        metadata.put("authorizedAt", authorizedAt);
        metadata.put("assignmentCount", assignmentSummary.assignmentCount());
        metadata.put("activeAssignmentCount", assignmentSummary.activeAssignmentCount());
        metadata.put("stoppedAssignmentCount", assignmentSummary.stoppedAssignmentCount());
        return metadata;
    }

    private Map<String, Object> runStopMetadata(ChaosRun run,
                                                Instant stopRequestedAt,
                                                RunAssignmentSummary assignmentSummary) {
        Map<String, Object> metadata = baseRunMetadata(run);
        metadata.put("stopRequestedAt", stopRequestedAt);
        metadata.put("assignmentCount", assignmentSummary.assignmentCount());
        metadata.put("activeAssignmentCount", assignmentSummary.activeAssignmentCount());
        metadata.put("stoppedAssignmentCount", assignmentSummary.stoppedAssignmentCount());
        return metadata;
    }

    private Map<String, Object> rollbackMetadata(ChaosRun run,
                                                 Instant rollbackVerifiedAt,
                                                 String reason,
                                                 RunAssignmentSummary assignmentSummary) {
        Map<String, Object> metadata = baseRunMetadata(run);
        metadata.put("rollbackVerifiedAt", rollbackVerifiedAt);
        metadata.put("rollbackReason", reason);
        metadata.put("endedAt", run.endedAt());
        metadata.put("finalStatus", run.status());
        metadata.put("assignmentCount", assignmentSummary.assignmentCount());
        metadata.put("activeAssignmentCount", assignmentSummary.activeAssignmentCount());
        metadata.put("stoppedAssignmentCount", assignmentSummary.stoppedAssignmentCount());
        return metadata;
    }

    private Map<String, Object> failureMetadata(ChaosRun run,
                                                String message,
                                                Instant reportedAt,
                                                RunAssignmentSummary assignmentSummary) {
        Map<String, Object> metadata = baseRunMetadata(run);
        metadata.put("failureReportedAt", reportedAt);
        metadata.put("failureMessage", message);
        metadata.put("endedAt", run.endedAt());
        metadata.put("finalStatus", run.status());
        metadata.put("assignmentCount", assignmentSummary.assignmentCount());
        metadata.put("activeAssignmentCount", assignmentSummary.activeAssignmentCount());
        metadata.put("stoppedAssignmentCount", assignmentSummary.stoppedAssignmentCount());
        return metadata;
    }

    private Map<String, Object> baseRunMetadata(ChaosRun run) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", run.targetEnvironment());
        metadata.put("targetSelector", run.targetSelector());
        metadata.put("faultType", run.faultType());
        metadata.put("requestedDurationSeconds", run.requestedDurationSeconds());
        metadata.put("startedAt", run.startedAt());
        if (run.experimentId() != null) {
            metadata.put("experimentId", run.experimentId().toString());
        }
        if (run.latencyMilliseconds() != null) {
            metadata.put("latencyMilliseconds", run.latencyMilliseconds());
        }
        if (run.latencyJitterMilliseconds() != null) {
            metadata.put("latencyJitterMilliseconds", run.latencyJitterMilliseconds());
        }
        if (run.latencyMinimumMilliseconds() != null) {
            metadata.put("latencyMinimumMilliseconds", run.latencyMinimumMilliseconds());
        }
        if (run.latencyMaximumMilliseconds() != null) {
            metadata.put("latencyMaximumMilliseconds", run.latencyMaximumMilliseconds());
        }
        if (run.errorCode() != null) {
            metadata.put("errorCode", run.errorCode());
        }
        if (run.trafficPercentage() != null) {
            metadata.put("trafficPercentage", run.trafficPercentage());
        }
        if (run.dropPercentage() != null) {
            metadata.put("dropPercentage", run.dropPercentage());
        }
        if (!run.routeFilters().isEmpty()) {
            metadata.put("routeFilters", run.routeFilters());
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
            case ROLLED_BACK -> "RUN_ALREADY_ROLLED_BACK";
            case STOPPED -> "RUN_ALREADY_STOPPED";
            case COMPLETED -> "RUN_ALREADY_COMPLETED";
            case FAILED -> "RUN_ALREADY_FAILED";
            case ACTIVE -> "RUN_STOP_NOT_ALLOWED";
        };
        String message = switch (currentStatus) {
            case STOP_REQUESTED -> "Run already has a pending stop request.";
            case ROLLED_BACK -> "Run has already been rolled back.";
            case STOPPED -> "Run has already been stopped.";
            case COMPLETED -> "Completed runs cannot be stopped again.";
            case FAILED -> "Failed runs cannot be stopped again.";
            case ACTIVE -> "Run stop is only allowed for active runs.";
        };
        return new RunStopValidationResponse(code, message, runId, currentStatus, List.of(ChaosRunStatus.ACTIVE));
    }

    private ChaosRun persistRun(DispatchAuthorizationResponse authorization,
                                UUID experimentId,
                                RunTargetSnapshot targetSnapshot) {
        Instant startedAt = authorization.authorizedAt();
        Instant rollbackScheduledAt = startedAt.plusSeconds(authorization.requestedDurationSeconds());
        ChaosRunEntity entity = new ChaosRunEntity(
                authorization.dispatchId(),
                experimentId,
                authorization.targetEnvironment(),
                authorization.targetSelector(),
                authorization.faultType(),
                authorization.requestedDurationSeconds(),
                authorization.latencyMilliseconds(),
                authorization.latencyJitterMilliseconds(),
                authorization.latencyMinimumMilliseconds(),
                authorization.latencyMaximumMilliseconds(),
                authorization.errorCode(),
                authorization.trafficPercentage(),
                authorization.dropPercentage(),
                authorization.routeFilters(),
                authorization.approvalId(),
                ChaosRunStatus.ACTIVE,
                startedAt,
                null,
                rollbackScheduledAt,
                null,
                startedAt,
                ChaosRunEntity.writeJson(objectMapper, targetSnapshot),
                null,
                null,
                null
        );
        return chaosRunJpaRepository.save(entity).toDomain(objectMapper);
    }

    private RunExecutionReportEntity saveReport(UUID runId,
                                                RunExecutionReportState state,
                                                String reportedBy,
                                                String message,
                                                Instant reportedAt) {
        return runExecutionReportJpaRepository.save(new RunExecutionReportEntity(
                UUID.randomUUID(),
                runId,
                state,
                reportedBy,
                message,
                reportedAt
        ));
    }

    private boolean supportsTelemetrySnapshots(ChaosRun run) {
        return "latency".equalsIgnoreCase(run.faultType())
                || "request_drop".equalsIgnoreCase(run.faultType())
                || "process_kill".equalsIgnoreCase(run.faultType())
                || "service_pause".equalsIgnoreCase(run.faultType());
    }

    private boolean supportsPeriodicTelemetry(ChaosRun run) {
        return "latency".equalsIgnoreCase(run.faultType())
                || "request_drop".equalsIgnoreCase(run.faultType());
    }

    private record RunScheduleHandle(ScheduledFuture<?> telemetryFuture, ScheduledFuture<?> rollbackFuture) {
        private void cancel() {
            if (telemetryFuture != null) {
                telemetryFuture.cancel(false);
            }
            if (rollbackFuture != null) {
                rollbackFuture.cancel(false);
            }
        }
    }
}
