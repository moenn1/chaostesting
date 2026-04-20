package com.myg.controlplane.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KillSwitchService {

    private static final long GLOBAL_KILL_SWITCH_ID = 1L;

    private final Clock clock;
    private final KillSwitchStateJpaRepository killSwitchStateJpaRepository;
    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final SafetyAuditRecordJpaRepository safetyAuditRecordJpaRepository;

    public KillSwitchService(Clock clock,
                             KillSwitchStateJpaRepository killSwitchStateJpaRepository,
                             ChaosRunJpaRepository chaosRunJpaRepository,
                             SafetyAuditRecordJpaRepository safetyAuditRecordJpaRepository) {
        this.clock = clock;
        this.killSwitchStateJpaRepository = killSwitchStateJpaRepository;
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.safetyAuditRecordJpaRepository = safetyAuditRecordJpaRepository;
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
    public KillSwitchStatusResponse enable(KillSwitchCommandRequest request) {
        Instant now = clock.instant();
        String operator = request.operator().trim();
        String reason = request.reason().trim();

        KillSwitchStateEntity stateEntity = loadOrCreate(now);
        stateEntity.enable(operator, reason, now);

        List<ChaosRunEntity> activeRuns = chaosRunJpaRepository.findAllByStatus(ChaosRunStatus.ACTIVE);
        activeRuns.forEach(run -> run.markStopRequested(operator, reason, now));

        killSwitchStateJpaRepository.save(stateEntity);
        safetyAuditRecordJpaRepository.save(new SafetyAuditRecordEntity(
                UUID.randomUUID(),
                SafetyAuditEventType.KILL_SWITCH_ENABLED,
                null,
                operator,
                reason,
                now
        ));
        Instant stopRecordedAt = now.plusMillis(1);
        activeRuns.forEach(run -> safetyAuditRecordJpaRepository.save(new SafetyAuditRecordEntity(
                UUID.randomUUID(),
                SafetyAuditEventType.RUN_STOP_REQUESTED,
                run.toDomain().id(),
                operator,
                reason,
                stopRecordedAt
        )));

        return statusResponse(stateEntity.toDomain());
    }

    @Transactional
    public KillSwitchStatusResponse disable(KillSwitchCommandRequest request) {
        Instant now = clock.instant();
        String operator = request.operator().trim();
        String reason = request.reason().trim();

        KillSwitchStateEntity stateEntity = loadOrCreate(now);
        stateEntity.disable(operator, reason, now);
        killSwitchStateJpaRepository.save(stateEntity);
        safetyAuditRecordJpaRepository.save(new SafetyAuditRecordEntity(
                UUID.randomUUID(),
                SafetyAuditEventType.KILL_SWITCH_DISABLED,
                null,
                operator,
                reason,
                now
        ));

        return statusResponse(stateEntity.toDomain());
    }

    @Transactional(readOnly = true)
    public List<SafetyAuditRecordResponse> listAuditRecords() {
        return safetyAuditRecordJpaRepository.findAllByOrderByRecordedAtDescIdDesc().stream()
                .map(SafetyAuditRecordEntity::toDomain)
                .map(SafetyAuditRecordResponse::from)
                .toList();
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
                chaosRunJpaRepository.countByStatus(ChaosRunStatus.STOP_REQUESTED)
        );
    }
}
