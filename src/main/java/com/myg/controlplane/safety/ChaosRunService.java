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
    private final LatencyTelemetrySnapshotJpaRepository latencyTelemetrySnapshotJpaRepository;
    private final AuditLogService auditLogService;
    private final TaskScheduler taskScheduler;
    private final LatencyInjectionProperties latencyInjectionProperties;
    private final ConcurrentMap<UUID, RunScheduleHandle> runtimeSchedules = new ConcurrentHashMap<>();

    public ChaosRunService(Clock clock,
                           ChaosRunJpaRepository chaosRunJpaRepository,
                           LatencyTelemetrySnapshotJpaRepository latencyTelemetrySnapshotJpaRepository,
                           AuditLogService auditLogService,
                           TaskScheduler taskScheduler,
                           LatencyInjectionProperties latencyInjectionProperties) {
        this.clock = clock;
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.latencyTelemetrySnapshotJpaRepository = latencyTelemetrySnapshotJpaRepository;
        this.auditLogService = auditLogService;
        this.taskScheduler = taskScheduler;
        this.latencyInjectionProperties = latencyInjectionProperties;
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
                authorization.latencyMilliseconds(),
                authorization.trafficPercentage(),
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
        latencyTelemetrySnapshotJpaRepository.save(LatencyTelemetrySnapshotEntity.injection(
                entity.toDomain(),
                authorization.authorizedAt(),
                "Latency injection activated."
        ));
        auditLogService.record(
                SafetyAuditEventType.RUN_STARTED,
                AuditResourceType.RUN,
                authorization.dispatchId().toString(),
                request.requestedBy().trim(),
                "Authorized chaos run for " + authorization.targetSelector(),
                runMetadata(entity.toDomain(), authorization.authorizedAt()),
                authorization.authorizedAt()
        );
        scheduleRuntime(entity.toDomain(), authorization.authorizedAt());
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
    public List<LatencyTelemetrySnapshotResponse> findTelemetry(UUID runId) {
        ensureRunExists(runId);
        return latencyTelemetrySnapshotJpaRepository.findAllByRunIdOrderByCapturedAtDescIdDesc(runId).stream()
                .map(LatencyTelemetrySnapshotEntity::toDomain)
                .map(LatencyTelemetrySnapshotResponse::from)
                .toList();
    }

    @Transactional
    public ChaosRunResponse stopRun(UUID runId, String operator, String reason) {
        ChaosRunEntity entity = loadRun(runId);
        rollbackRun(entity, operator.trim(), reason.trim(), clock.instant());
        return ChaosRunResponse.from(entity.toDomain());
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
        chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.ACTIVE).forEach(run -> {
            if (!run.toDomain().rollbackScheduledAt().isAfter(now)) {
                rollbackRun(run, SYSTEM_OPERATOR, TIMEBOX_COMPLETED_REASON, now);
            } else {
                scheduleRuntime(run.toDomain(), now);
            }
        });
        chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.STOP_REQUESTED)
                .forEach(run -> rollbackRun(run, SYSTEM_OPERATOR, "resume pending rollback on startup", now));
    }

    @Transactional
    void emitScheduledTelemetry(UUID runId) {
        chaosRunJpaRepository.findById(runId).ifPresent(run -> {
            if (run.toDomain().status() != ChaosRunStatus.ACTIVE) {
                cancelRuntime(runId);
                return;
            }
            latencyTelemetrySnapshotJpaRepository.save(LatencyTelemetrySnapshotEntity.injection(
                    run.toDomain(),
                    clock.instant(),
                    "Latency injection remains active."
            ));
        });
    }

    @Transactional
    void rollbackDueRun(UUID runId) {
        chaosRunJpaRepository.findById(runId).ifPresent(run -> {
            if (run.toDomain().status() == ChaosRunStatus.ACTIVE) {
                rollbackRun(run, SYSTEM_OPERATOR, TIMEBOX_COMPLETED_REASON, clock.instant());
            } else {
                cancelRuntime(runId);
            }
        });
    }

    private void scheduleRuntime(ChaosRun run, Instant scheduledFrom) {
        cancelRuntime(run.id());
        Instant firstTelemetryAt = scheduledFrom.plus(latencyInjectionProperties.getTelemetryInterval());
        ScheduledFuture<?> telemetryFuture = null;
        if (firstTelemetryAt.isBefore(run.rollbackScheduledAt())) {
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
        if (entity.toDomain().status() == ChaosRunStatus.ROLLED_BACK) {
            cancelRuntime(entity.toDomain().id());
            return;
        }

        cancelRuntime(entity.toDomain().id());
        if (entity.toDomain().status() == ChaosRunStatus.ACTIVE) {
            entity.markStopRequested(operator, reason, now);
            auditLogService.record(
                    SafetyAuditEventType.RUN_STOP_REQUESTED,
                    AuditResourceType.RUN,
                    entity.toDomain().id().toString(),
                    operator,
                    "Stop requested for " + entity.toDomain().targetSelector(),
                    runStopMetadata(entity.toDomain(), now),
                    now
            );
        }

        Instant rollbackVerifiedAt = now.plusMillis(1);
        entity.markRolledBack(rollbackVerifiedAt);
        chaosRunJpaRepository.save(entity);
        latencyTelemetrySnapshotJpaRepository.save(LatencyTelemetrySnapshotEntity.rollback(
                entity.toDomain(),
                rollbackVerifiedAt,
                "Rollback verified after stop."
        ));
        auditLogService.record(
                SafetyAuditEventType.RUN_ROLLBACK_VERIFIED,
                AuditResourceType.RUN,
                entity.toDomain().id().toString(),
                operator,
                "Rollback verified for " + entity.toDomain().targetSelector(),
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

    private void cancelRuntime(UUID runId) {
        RunScheduleHandle handle = runtimeSchedules.remove(runId);
        if (handle == null) {
            return;
        }
        handle.cancel();
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

    private Map<String, Object> baseRunMetadata(ChaosRun run) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", run.targetEnvironment());
        metadata.put("targetSelector", run.targetSelector());
        metadata.put("faultType", run.faultType());
        metadata.put("requestedDurationSeconds", run.requestedDurationSeconds());
        if (run.latencyMilliseconds() != null) {
            metadata.put("latencyMilliseconds", run.latencyMilliseconds());
        }
        if (run.trafficPercentage() != null) {
            metadata.put("trafficPercentage", run.trafficPercentage());
        }
        if (run.approvalId() != null) {
            metadata.put("approvalId", run.approvalId().toString());
        }
        return metadata;
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
