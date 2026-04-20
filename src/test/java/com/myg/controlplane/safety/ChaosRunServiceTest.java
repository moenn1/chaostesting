package com.myg.controlplane.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    private final LatencyTelemetrySnapshotJpaRepository latencyTelemetrySnapshotJpaRepository =
            Mockito.mock(LatencyTelemetrySnapshotJpaRepository.class);
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
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
                .thenReturn(mock(ScheduledFuture.class));

        chaosRunService = new ChaosRunService(
                clock,
                chaosRunJpaRepository,
                latencyTelemetrySnapshotJpaRepository,
                auditLogService,
                runLifecycleEventService,
                objectMapper,
                taskScheduler,
                latencyInjectionProperties
        );
    }

    @Test
    void timedRunRollsBackWhenDeadlineTaskExecutes() {
        UUID runId = UUID.randomUUID();
        DispatchAuthorizationResponse authorization = new DispatchAuthorizationResponse(
                runId,
                "AUTHORIZED",
                "staging",
                "checkout-service",
                "latency",
                120,
                350,
                30,
                null,
                clock.instant()
        );
        RunDispatchRequest request = new RunDispatchRequest(
                "staging",
                "checkout-service",
                "latency",
                120,
                350,
                30,
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
