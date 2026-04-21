package com.myg.controlplane.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myg.controlplane.agents.service.AgentRegistryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

class ChaosRunServiceTest {

    private final ChaosRunJpaRepository chaosRunJpaRepository = Mockito.mock(ChaosRunJpaRepository.class);
    private final RunAssignmentJpaRepository runAssignmentJpaRepository = Mockito.mock(RunAssignmentJpaRepository.class);
    private final AgentRegistryService agentRegistryService = Mockito.mock(AgentRegistryService.class);
    private final LatencyTelemetrySnapshotJpaRepository latencyTelemetrySnapshotJpaRepository =
            Mockito.mock(LatencyTelemetrySnapshotJpaRepository.class);
    private final RunExecutionReportJpaRepository runExecutionReportJpaRepository =
            Mockito.mock(RunExecutionReportJpaRepository.class);
    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
    private final RunLifecycleEventService runLifecycleEventService = Mockito.mock(RunLifecycleEventService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskScheduler taskScheduler = Mockito.mock(TaskScheduler.class);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-04-20T16:00:00Z"), ZoneOffset.UTC);
    private final AtomicReference<ChaosRunEntity> storedRun = new AtomicReference<>();

    private ChaosRunService chaosRunService;

    @BeforeEach
    void setUp() {
        LatencyInjectionProperties latencyInjectionProperties = new LatencyInjectionProperties();
        latencyInjectionProperties.setMaxLatency(Duration.ofSeconds(5));
        latencyInjectionProperties.setTelemetryInterval(Duration.ofSeconds(30));

        when(chaosRunJpaRepository.save(any(ChaosRunEntity.class))).thenAnswer(invocation -> {
            ChaosRunEntity entity = invocation.getArgument(0);
            storedRun.set(entity);
            return entity;
        });
        when(chaosRunJpaRepository.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(storedRun.get()));
        when(chaosRunJpaRepository.existsById(any(UUID.class))).thenAnswer(invocation -> storedRun.get() != null);
        when(runAssignmentJpaRepository.findAllByRunId(any(UUID.class))).thenReturn(List.of());
        when(runAssignmentJpaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRegistryService.findAll(any(), any(), any(), any())).thenReturn(List.of());
        when(runExecutionReportJpaRepository.save(any(RunExecutionReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));

        chaosRunService = new ChaosRunService(
                clock,
                chaosRunJpaRepository,
                runAssignmentJpaRepository,
                agentRegistryService,
                latencyTelemetrySnapshotJpaRepository,
                runExecutionReportJpaRepository,
                auditLogService,
                runLifecycleEventService,
                objectMapper,
                taskScheduler,
                latencyInjectionProperties
        );
    }

    @Test
    void timedLatencyRunRollsBackWhenDeadlineTaskExecutes() {
        UUID runId = UUID.randomUUID();
        DispatchAuthorizationResponse authorization = new DispatchAuthorizationResponse(
                runId,
                "AUTHORIZED",
                "staging",
                "checkout-service",
                "latency",
                120,
                350,
                40,
                null,
                null,
                null,
                30,
                null,
                List.of(),
                null,
                clock.instant()
        );
        RunDispatchRequest request = new RunDispatchRequest(
                "staging",
                "checkout-service",
                "latency",
                120,
                350,
                40,
                null,
                null,
                null,
                30,
                null,
                List.of(),
                null,
                "experiment-operator"
        );

        ArgumentCaptor<Runnable> rollbackTaskCaptor = ArgumentCaptor.forClass(Runnable.class);

        chaosRunService.startAuthorizedRun(authorization, request);

        verify(taskScheduler).schedule(rollbackTaskCaptor.capture(), any(Instant.class));
        assertThat(storedRun.get().toDomain().status()).isEqualTo(ChaosRunStatus.ACTIVE);

        clock.advanceSeconds(121);
        rollbackTaskCaptor.getValue().run();

        ChaosRun run = storedRun.get().toDomain();
        assertThat(run.status()).isEqualTo(ChaosRunStatus.ROLLED_BACK);
        assertThat(run.rollbackVerifiedAt()).isNotNull();
        assertThat(run.stopCommandIssuedBy()).isEqualTo("system");
        assertThat(run.stopCommandReason()).isEqualTo("requested duration elapsed");

        verify(latencyTelemetrySnapshotJpaRepository, Mockito.times(2)).save(any(LatencyTelemetrySnapshotEntity.class));
        verify(runExecutionReportJpaRepository).save(any(RunExecutionReportEntity.class));

        ArgumentCaptor<SafetyAuditEventType> actionCaptor = ArgumentCaptor.forClass(SafetyAuditEventType.class);
        verify(auditLogService, Mockito.times(3)).record(
                actionCaptor.capture(),
                any(AuditResourceType.class),
                any(String.class),
                any(String.class),
                any(String.class),
                any(Map.class),
                any(Instant.class)
        );
        assertThat(actionCaptor.getAllValues())
                .containsExactly(
                        SafetyAuditEventType.RUN_STARTED,
                        SafetyAuditEventType.RUN_STOP_REQUESTED,
                        SafetyAuditEventType.RUN_ROLLBACK_VERIFIED
                );
    }

    @Test
    void failureReportMarksHttpErrorRunFailedAndPersistsExecutionReport() {
        UUID runId = UUID.randomUUID();
        DispatchAuthorizationResponse authorization = new DispatchAuthorizationResponse(
                runId,
                "AUTHORIZED",
                "staging",
                "checkout-service",
                "http_error",
                120,
                null,
                null,
                null,
                null,
                503,
                30,
                null,
                List.of("/checkout"),
                null,
                clock.instant()
        );
        RunDispatchRequest request = new RunDispatchRequest(
                "staging",
                "checkout-service",
                "http_error",
                120,
                null,
                null,
                null,
                null,
                503,
                30,
                null,
                List.of("/checkout"),
                null,
                "experiment-operator"
        );

        chaosRunService.startAuthorizedRun(authorization, request);
        clock.advanceSeconds(1);

        RunExecutionReportResponse response = chaosRunService.reportRun(
                runId,
                "agent-eu-1",
                new RunExecutionReportRequest(
                        RunExecutionReportState.FAILURE,
                        "Failed to attach scoped route filter."
                )
        );

        ChaosRun run = storedRun.get().toDomain();
        assertThat(run.status()).isEqualTo(ChaosRunStatus.FAILED);
        assertThat(run.endedAt()).isEqualTo(clock.instant());
        assertThat(response.state()).isEqualTo(RunExecutionReportState.FAILURE);
        assertThat(response.reportedBy()).isEqualTo("agent-eu-1");
        verify(runExecutionReportJpaRepository).save(any(RunExecutionReportEntity.class));
    }

    @Test
    void requestDropRunPersistsFaultShapeAcrossTelemetryAndRollback() {
        UUID runId = UUID.randomUUID();
        DispatchAuthorizationResponse authorization = new DispatchAuthorizationResponse(
                runId,
                "AUTHORIZED",
                "staging",
                "edge-gateway",
                "request_drop",
                90,
                null,
                null,
                null,
                null,
                null,
                null,
                12,
                List.of(),
                null,
                clock.instant()
        );
        RunDispatchRequest request = new RunDispatchRequest(
                "staging",
                "edge-gateway",
                "request_drop",
                90,
                null,
                null,
                null,
                null,
                null,
                null,
                12,
                List.of(),
                null,
                "experiment-operator"
        );

        chaosRunService.startAuthorizedRun(authorization, request);
        ChaosRunResponse stopped = chaosRunService.stopRun(runId, "ops-oncall", "containment");

        assertThat(stopped.faultType()).isEqualTo("request_drop");
        assertThat(stopped.dropPercentage()).isEqualTo(12);

        ArgumentCaptor<LatencyTelemetrySnapshotEntity> telemetryCaptor =
                ArgumentCaptor.forClass(LatencyTelemetrySnapshotEntity.class);
        verify(latencyTelemetrySnapshotJpaRepository, Mockito.times(2)).save(telemetryCaptor.capture());
        assertThat(telemetryCaptor.getAllValues())
                .extracting(LatencyTelemetrySnapshotEntity::toDomain)
                .extracting(LatencyTelemetrySnapshot::faultType)
                .containsExactly("request_drop", "request_drop");
    }

    @Test
    void processKillRunsSkipPeriodicTelemetryAndPersistRecoveryMessages() {
        UUID runId = UUID.randomUUID();
        DispatchAuthorizationResponse authorization = new DispatchAuthorizationResponse(
                runId,
                "AUTHORIZED",
                "staging",
                "checkout-worker",
                "process_kill",
                90,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                clock.instant()
        );
        RunDispatchRequest request = new RunDispatchRequest(
                "staging",
                "checkout-worker",
                "process_kill",
                90,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                "experiment-operator"
        );
        ArgumentCaptor<Runnable> rollbackTaskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<LatencyTelemetrySnapshotEntity> telemetryCaptor =
                ArgumentCaptor.forClass(LatencyTelemetrySnapshotEntity.class);

        chaosRunService.startAuthorizedRun(authorization, request);

        verify(taskScheduler, Mockito.never()).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
        verify(taskScheduler).schedule(rollbackTaskCaptor.capture(), any(Instant.class));

        clock.advanceSeconds(91);
        rollbackTaskCaptor.getValue().run();

        verify(latencyTelemetrySnapshotJpaRepository, Mockito.times(2)).save(telemetryCaptor.capture());
        assertThat(telemetryCaptor.getAllValues())
                .extracting(LatencyTelemetrySnapshotEntity::toDomain)
                .extracting(LatencyTelemetrySnapshot::message)
                .containsExactly(
                        "Process kill against checkout-worker activated.",
                        "Process recovery verified after requested duration elapsed."
                );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zoneId;

        private MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
