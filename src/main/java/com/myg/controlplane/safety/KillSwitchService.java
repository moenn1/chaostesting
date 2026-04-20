package com.myg.controlplane.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KillSwitchService {

    private static final long GLOBAL_KILL_SWITCH_ID = 1L;

    private final Clock clock;
    private final KillSwitchStateJpaRepository killSwitchStateJpaRepository;
    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final ChaosRunService chaosRunService;
    private final AuditLogService auditLogService;

    public KillSwitchService(Clock clock,
                             KillSwitchStateJpaRepository killSwitchStateJpaRepository,
                             ChaosRunJpaRepository chaosRunJpaRepository,
                             ChaosRunService chaosRunService,
                             AuditLogService auditLogService) {
        this.clock = clock;
        this.killSwitchStateJpaRepository = killSwitchStateJpaRepository;
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.chaosRunService = chaosRunService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled() {
        return currentState().enabled();
    }

    @Transactional(readOnly = true)
    public KillSwitchStatusResponse getStatus() {
        return statusResponse(currentState(), 0);
    }

    @Transactional
    public KillSwitchStatusResponse enable(String operator, KillSwitchCommandRequest request) {
        Instant now = clock.instant();
        String normalizedOperator = operator.trim();
        String reason = request.reason().trim();

        KillSwitchStateEntity stateEntity = loadOrCreate(now);
        stateEntity.enable(normalizedOperator, reason, now);

        long stopRequestsIssued = chaosRunService.stopActiveRuns(normalizedOperator, reason);

        killSwitchStateJpaRepository.save(stateEntity);
        auditLogService.record(
                SafetyAuditEventType.KILL_SWITCH_ENABLED,
                AuditResourceType.KILL_SWITCH,
                "global",
                normalizedOperator,
                reason,
                Map.of("affectedActiveRunCount", stopRequestsIssued, "enabledAt", now),
                now
        );

        return statusResponse(stateEntity.toDomain(), stopRequestsIssued);
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

        return statusResponse(stateEntity.toDomain(), 0);
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

    private KillSwitchStatusResponse statusResponse(KillSwitchState state, long stopRequestsIssued) {
        return KillSwitchStatusResponse.from(
                state,
                chaosRunJpaRepository.countByStatus(ChaosRunStatus.ACTIVE),
                chaosRunJpaRepository.countByStatus(ChaosRunStatus.STOP_REQUESTED),
                chaosRunJpaRepository.countByStatus(ChaosRunStatus.ROLLED_BACK),
                stopRequestsIssued
        );
    }
}
