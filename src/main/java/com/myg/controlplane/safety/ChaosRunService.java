package com.myg.controlplane.safety;

import java.time.Clock;
import java.time.Instant;
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
    private final RunExecutionReportJpaRepository runExecutionReportJpaRepository;
    private final AuditLogService auditLogService;
    private final TaskScheduler taskScheduler;
    private final ConcurrentMap<UUID, ScheduledFuture<?>> rollbackSchedules = new ConcurrentHashMap<>();

    public ChaosRunService(Clock clock,
                           ChaosRunJpaRepository chaosRunJpaRepository,
                           RunExecutionReportJpaRepository runExecutionReportJpaRepository,
                           AuditLogService auditLogService,
                           TaskScheduler taskScheduler) {
        this.clock = clock;
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.runExecutionReportJpaRepository = runExecutionReportJpaRepository;
        this.auditLogService = auditLogService;
        this.taskScheduler = taskScheduler;
    }

    @Transactional
    public void startAuthorizedRun(DispatchAuthorizationResponse authorization, RunDispatchRequest request) {
        Instant rollbackScheduledAt = authorization.authorizedAt().plusSeconds(authorization.requestedDurationSeconds());
        ChaosRunEntity entity = new ChaosRunEntity(
                authorization.dispatchId(),
                authorization.targetEnvironment(),
                authorization.targetSelector(),
                authorization.faultType(),
                authorization.requestedDurationSeconds(),
                authorization.errorCode(),
                authorization.trafficPercentage(),
                authorization.routeFilters(),
                authorization.approvalId(),
                ChaosRunStatus.ACTIVE,
                authorization.authorizedAt(),
                rollbackScheduledAt,
                null,
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
                runMetadata(entity.toDomain(), authorization.authorizedAt()),
                authorization.authorizedAt()
        );
        scheduleRollback(entity.toDomain());
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

    @Transactional(readOnly = true)
    public ChaosRunResponse getRun(UUID runId) {
        return ChaosRunResponse.from(loadRun(runId).toDomain());
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
    public RunExecutionReportResponse reportRun(UUID runId, RunExecutionReportRequest request) {
        ChaosRunEntity entity = loadRun(runId);
        Instant now = clock.instant();
        String reportedBy = request.reportedBy().trim();
        String message = request.message().trim();

        if (request.state() == RunExecutionReportState.FAILURE
                && entity.toDomain().status() != ChaosRunStatus.ROLLED_BACK) {
            cancelRollback(runId);
            entity.markFailed();
            auditLogService.record(
                    SafetyAuditEventType.RUN_EXECUTION_FAILED,
                    AuditResourceType.RUN,
                    runId.toString(),
                    reportedBy,
                    "Execution failed for " + entity.toDomain().targetSelector(),
                    failureMetadata(entity.toDomain(), message, now),
                    now
            );
        } else if (request.state() == RunExecutionReportState.ROLLBACK
                && entity.toDomain().status() != ChaosRunStatus.ROLLED_BACK) {
            cancelRollback(runId);
            entity.markRolledBack(now);
            auditLogService.record(
                    SafetyAuditEventType.RUN_ROLLBACK_VERIFIED,
                    AuditResourceType.RUN,
                    runId.toString(),
                    reportedBy,
                    "Rollback verified for " + entity.toDomain().targetSelector(),
                    rollbackMetadata(entity.toDomain(), now, message),
                    now
            );
        }

        return RunExecutionReportResponse.from(saveReport(runId, request.state(), reportedBy, message, now).toDomain());
    }

    @Transactional
    public ChaosRunResponse stopRun(UUID runId, RunStopRequest request) {
        ChaosRunEntity entity = loadRun(runId);
        rollbackRun(entity, request.operator().trim(), request.reason().trim(), clock.instant());
        return ChaosRunResponse.from(entity.toDomain());
    }

    @Transactional
    public long stopActiveRuns(String operator, String reason) {
        Instant now = clock.instant();
        List<ChaosRunEntity> activeRuns = chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.ACTIVE);
        activeRuns.forEach(run -> rollbackRun(run, operator, reason, now));
        return activeRuns.size();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void restoreRuntimeSchedules() {
        Instant now = clock.instant();
        chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.ACTIVE).forEach(run -> {
            if (!run.toDomain().rollbackScheduledAt().isAfter(now)) {
                rollbackRun(run, SYSTEM_OPERATOR, TIMEBOX_COMPLETED_REASON, now);
            } else {
                scheduleRollback(run.toDomain());
            }
        });
        chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.STOP_REQUESTED)
                .forEach(run -> rollbackRun(run, SYSTEM_OPERATOR, "resume pending rollback on startup", now));
    }

    @Transactional
    void rollbackDueRun(UUID runId) {
        chaosRunJpaRepository.findById(runId).ifPresent(run -> {
            if (run.toDomain().status() == ChaosRunStatus.ACTIVE) {
                rollbackRun(run, SYSTEM_OPERATOR, TIMEBOX_COMPLETED_REASON, clock.instant());
            } else {
                cancelRollback(runId);
            }
        });
    }

    private void scheduleRollback(ChaosRun run) {
        cancelRollback(run.id());
        ScheduledFuture<?> rollbackFuture = taskScheduler.schedule(
                () -> rollbackDueRun(run.id()),
                run.rollbackScheduledAt()
        );
        rollbackSchedules.put(run.id(), rollbackFuture);
    }

    private void rollbackRun(ChaosRunEntity entity, String operator, String reason, Instant now) {
        ChaosRun current = entity.toDomain();
        if (current.status() == ChaosRunStatus.ROLLED_BACK) {
            cancelRollback(current.id());
            return;
        }

        cancelRollback(current.id());
        if (current.status() == ChaosRunStatus.ACTIVE) {
            entity.markStopRequested(operator, reason, now);
            auditLogService.record(
                    SafetyAuditEventType.RUN_STOP_REQUESTED,
                    AuditResourceType.RUN,
                    current.id().toString(),
                    operator,
                    "Stop requested for " + current.targetSelector(),
                    runStopMetadata(current, now),
                    now
            );
        }

        Instant rollbackVerifiedAt = now.plusMillis(1);
        entity.markRolledBack(rollbackVerifiedAt);
        saveReport(
                current.id(),
                RunExecutionReportState.ROLLBACK,
                operator,
                "Rollback verified after " + reason + ".",
                rollbackVerifiedAt
        );
        auditLogService.record(
                SafetyAuditEventType.RUN_ROLLBACK_VERIFIED,
                AuditResourceType.RUN,
                current.id().toString(),
                operator,
                "Rollback verified for " + current.targetSelector(),
                rollbackMetadata(entity.toDomain(), rollbackVerifiedAt, reason),
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

    private void cancelRollback(UUID runId) {
        ScheduledFuture<?> rollbackFuture = rollbackSchedules.remove(runId);
        if (rollbackFuture != null) {
            rollbackFuture.cancel(false);
        }
    }

    private Map<String, Object> runMetadata(ChaosRun run, Instant authorizedAt) {
        Map<String, Object> metadata = baseRunMetadata(run);
        metadata.put("authorizedAt", authorizedAt);
        return metadata;
    }

    private Map<String, Object> runStopMetadata(ChaosRun run, Instant stopRequestedAt) {
        Map<String, Object> metadata = baseRunMetadata(run);
        metadata.put("stopRequestedAt", stopRequestedAt);
        return metadata;
    }

    private Map<String, Object> rollbackMetadata(ChaosRun run, Instant rollbackVerifiedAt, String reason) {
        Map<String, Object> metadata = baseRunMetadata(run);
        metadata.put("rollbackVerifiedAt", rollbackVerifiedAt);
        metadata.put("rollbackReason", reason);
        return metadata;
    }

    private Map<String, Object> failureMetadata(ChaosRun run, String message, Instant reportedAt) {
        Map<String, Object> metadata = baseRunMetadata(run);
        metadata.put("failureReportedAt", reportedAt);
        metadata.put("failureMessage", message);
        return metadata;
    }

    private Map<String, Object> baseRunMetadata(ChaosRun run) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", run.targetEnvironment());
        metadata.put("targetSelector", run.targetSelector());
        metadata.put("faultType", run.faultType());
        metadata.put("requestedDurationSeconds", run.requestedDurationSeconds());
        if (run.errorCode() != null) {
            metadata.put("errorCode", run.errorCode());
        }
        if (run.trafficPercentage() != null) {
            metadata.put("trafficPercentage", run.trafficPercentage());
        }
        if (!run.routeFilters().isEmpty()) {
            metadata.put("routeFilters", run.routeFilters());
        }
        if (run.approvalId() != null) {
            metadata.put("approvalId", run.approvalId().toString());
        }
        return metadata;
    }
}
