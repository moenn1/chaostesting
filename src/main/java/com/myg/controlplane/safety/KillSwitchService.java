package com.myg.controlplane.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KillSwitchService {

    private static final long GLOBAL_KILL_SWITCH_ID = 1L;

    private final Clock clock;
    private final KillSwitchStateJpaRepository killSwitchStateJpaRepository;
    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final RunAssignmentJpaRepository runAssignmentJpaRepository;
    private final AuditLogService auditLogService;

    public KillSwitchService(Clock clock,
                             KillSwitchStateJpaRepository killSwitchStateJpaRepository,
                             ChaosRunJpaRepository chaosRunJpaRepository,
                             RunAssignmentJpaRepository runAssignmentJpaRepository,
                             AuditLogService auditLogService) {
        this.clock = clock;
        this.killSwitchStateJpaRepository = killSwitchStateJpaRepository;
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.runAssignmentJpaRepository = runAssignmentJpaRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return currentState().enabled();
    }

    @Transactional(readOnly = true)
    public KillSwitchStatusResponse getStatus() {
        return statusResponse(currentState());
    }

    @Transactional
    public KillSwitchStatusResponse enable(String operator, KillSwitchCommandRequest request) {
        Instant now = clock.instant();
        String normalizedOperator = operator.trim();
        String reason = request.reason().trim();

        KillSwitchStateEntity stateEntity = loadOrCreate(now);
        stateEntity.enable(normalizedOperator, reason, now);

        List<ChaosRunEntity> activeRuns = chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.ACTIVE);
        activeRuns.forEach(run -> run.markStopped(normalizedOperator, reason, now));
        List<RunAssignmentEntity> updatedAssignments = activeRuns.isEmpty()
                ? List.of()
                : runAssignmentJpaRepository.findAllByRunIdIn(
                        activeRuns.stream().map(ChaosRunEntity::toDomain).map(ChaosRun::id).toList()
                );
        updatedAssignments.forEach(assignment -> assignment.markStopped(now));

        killSwitchStateJpaRepository.save(stateEntity);
        chaosRunJpaRepository.saveAll(activeRuns);
        runAssignmentJpaRepository.saveAll(updatedAssignments);
        auditLogService.record(
                SafetyAuditEventType.KILL_SWITCH_ENABLED,
                AuditResourceType.KILL_SWITCH,
                "global",
                normalizedOperator,
                reason,
                Map.of("affectedActiveRunCount", activeRuns.size(), "enabledAt", now),
                now
        );
        for (int index = 0; index < activeRuns.size(); index++) {
            ChaosRun run = activeRuns.get(index).toDomain();
            auditLogService.record(
                    SafetyAuditEventType.RUN_STOP_REQUESTED,
                    AuditResourceType.RUN,
                    run.id().toString(),
                    normalizedOperator,
                    reason,
                    runStopMetadata(run, now),
                    now.plusMillis(index + 1L)
            );
        }

        return statusResponse(stateEntity.toDomain());
    }

    @Transactional
    public KillSwitchStatusResponse disable(String operator, KillSwitchCommandRequest request) {
        Instant now = clock.instant();
        String normalizedOperator = operator.trim();
        String reason = request.reason().trim();

        KillSwitchStateEntity stateEntity = loadOrCreate(now);
        stateEntity.disable(normalizedOperator, reason, now);
        killSwitchStateJpaRepository.save(stateEntity);
        auditLogService.record(
                SafetyAuditEventType.KILL_SWITCH_DISABLED,
                AuditResourceType.KILL_SWITCH,
                "global",
                normalizedOperator,
                reason,
                Map.of("enabled", false, "disabledAt", now),
                now
        );

        return statusResponse(stateEntity.toDomain());
    }

    private KillSwitchState currentState() {
        return killSwitchStateJpaRepository.findById(GLOBAL_KILL_SWITCH_ID)
                .map(KillSwitchStateEntity::toDomain)
                .orElseGet(() -> new KillSwitchState(false, null, null, null, null, null, null, null));
    }

    private KillSwitchStateEntity loadOrCreate(Instant now) {
        return killSwitchStateJpaRepository.findById(GLOBAL_KILL_SWITCH_ID)
                .orElseGet(() -> new KillSwitchStateEntity(GLOBAL_KILL_SWITCH_ID, false, now));
    }

    private KillSwitchStatusResponse statusResponse(KillSwitchState state) {
        return KillSwitchStatusResponse.from(
                state,
                chaosRunJpaRepository.countByStatus(ChaosRunStatus.ACTIVE),
                chaosRunJpaRepository.countByStatus(ChaosRunStatus.STOP_REQUESTED),
                chaosRunJpaRepository.countByStatus(ChaosRunStatus.STOPPED)
        );
    }

    private Map<String, Object> runStopMetadata(ChaosRun run, Instant stopRequestedAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetEnvironment", run.targetEnvironment());
        metadata.put("targetSelector", run.targetSelector());
        metadata.put("faultType", run.faultType());
        metadata.put("requestedDurationSeconds", run.requestedDurationSeconds());
        if (run.approvalId() != null) {
            metadata.put("approvalId", run.approvalId().toString());
        }
        metadata.put("stopRequestedAt", stopRequestedAt);
        metadata.put("endedAt", run.endedAt());
        metadata.put("finalStatus", run.status());
        return metadata;
    }
}
