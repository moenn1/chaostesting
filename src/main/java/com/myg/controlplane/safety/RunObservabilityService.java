package com.myg.controlplane.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunObservabilityService {

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ChaosRunJpaRepository chaosRunJpaRepository;
    private final LatencyTelemetrySnapshotJpaRepository latencyTelemetrySnapshotJpaRepository;
    private final AuditLogService auditLogService;

    public RunObservabilityService(Clock clock,
                                   ObjectMapper objectMapper,
                                   ChaosRunJpaRepository chaosRunJpaRepository,
                                   LatencyTelemetrySnapshotJpaRepository latencyTelemetrySnapshotJpaRepository,
                                   AuditLogService auditLogService) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.chaosRunJpaRepository = chaosRunJpaRepository;
        this.latencyTelemetrySnapshotJpaRepository = latencyTelemetrySnapshotJpaRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public RunMetricsResponse getMetrics(UUID runId) {
        return toMetrics(loadBundle(runId));
    }

    @Transactional(readOnly = true)
    public RunTraceResponse getTraces(UUID runId) {
        return toTraces(loadBundle(runId));
    }

    @Transactional(readOnly = true)
    public RunDiagnosticsResponse getDiagnostics(UUID runId) {
        ObservationBundle bundle = loadBundle(runId);
        return new RunDiagnosticsResponse(
                bundle.run().id(),
                clock.instant(),
                ChaosRunResponse.from(bundle.run()),
                toMetrics(bundle),
                toTraces(bundle),
                bundle.telemetry(),
                bundle.auditRecords()
        );
    }

    private ObservationBundle loadBundle(UUID runId) {
        ChaosRun run = chaosRunJpaRepository.findById(runId)
                .map(entity -> entity.toDomain(objectMapper))
                .orElseThrow(() -> new ChaosRunNotFoundException(runId));
        List<LatencyTelemetrySnapshotResponse> telemetry = latencyTelemetrySnapshotJpaRepository
                .findAllByRunIdOrderByCapturedAtAscIdAsc(runId).stream()
                .map(LatencyTelemetrySnapshotEntity::toDomain)
                .map(LatencyTelemetrySnapshotResponse::from)
                .toList();
        List<SafetyAuditRecordResponse> auditRecords = auditLogService.findRunRecords(runId);
        return new ObservationBundle(run, telemetry, auditRecords);
    }

    private RunMetricsResponse toMetrics(ObservationBundle bundle) {
        List<RunMetricPointResponse> series = bundle.telemetry().stream()
                .map(RunMetricPointResponse::from)
                .toList();
        List<RunMetricPointResponse> injectionPoints = series.stream()
                .filter(point -> point.phase() == LatencyTelemetryPhase.INJECTION)
                .toList();
        Instant startedAt = runStartedAt(bundle.run());
        Instant lastObservedAt = series.isEmpty()
                ? startedAt
                : series.get(series.size() - 1).capturedAt();
        Instant terminalObservedAt = terminalObservedAt(bundle.run());
        Instant observedUntil = terminalObservedAt != null
                ? terminalObservedAt
                : maxInstant(lastObservedAt, clock.instant());
        long observedDurationSeconds = Math.max(0, Duration.between(startedAt, observedUntil).getSeconds());
        int maxLatencyMilliseconds = injectionPoints.stream()
                .mapToInt(RunMetricPointResponse::latencyMilliseconds)
                .max()
                .orElse(0);
        int averageLatencyMilliseconds = (int) Math.round(injectionPoints.stream()
                .mapToInt(RunMetricPointResponse::latencyMilliseconds)
                .average()
                .orElse(0));
        int maxTrafficPercentage = injectionPoints.stream()
                .mapToInt(RunMetricPointResponse::trafficPercentage)
                .max()
                .orElse(0);
        int rollbackPointCount = (int) series.stream()
                .filter(point -> point.phase() == LatencyTelemetryPhase.ROLLBACK)
                .count();

        return new RunMetricsResponse(
                bundle.run().id(),
                bundle.run().status(),
                bundle.run().targetEnvironment(),
                bundle.run().targetSelector(),
                bundle.run().faultType(),
                bundle.run().latencyMilliseconds(),
                bundle.run().latencyJitterMilliseconds(),
                bundle.run().latencyMinimumMilliseconds(),
                bundle.run().latencyMaximumMilliseconds(),
                bundle.run().errorCode(),
                bundle.run().trafficPercentage(),
                bundle.run().dropPercentage(),
                bundle.run().routeFilters(),
                new RunMetricsSummaryResponse(
                        bundle.run().requestedDurationSeconds(),
                        observedDurationSeconds,
                        startedAt,
                        lastObservedAt,
                        bundle.run().rollbackScheduledAt(),
                        bundle.run().rollbackVerifiedAt(),
                        series.size(),
                        injectionPoints.size(),
                        rollbackPointCount,
                        maxLatencyMilliseconds,
                        averageLatencyMilliseconds,
                        maxTrafficPercentage,
                        bundle.run().rollbackVerifiedAt() != null
                ),
                series
        );
    }

    private RunTraceResponse toTraces(ObservationBundle bundle) {
        List<RunTraceReferenceResponse> traces = new ArrayList<>();
        if (!bundle.auditRecords().isEmpty()) {
            traces.add(toAuditTrace(bundle));
        }
        if (!bundle.telemetry().isEmpty()) {
            traces.add(toTelemetryTrace(bundle));
        }
        return new RunTraceResponse(bundle.run().id(), traces);
    }

    private RunTraceReferenceResponse toAuditTrace(ObservationBundle bundle) {
        List<SafetyAuditRecordResponse> auditRecords = bundle.auditRecords();
        SafetyAuditRecordResponse first = auditRecords.get(0);
        SafetyAuditRecordResponse last = auditRecords.get(auditRecords.size() - 1);
        LinkedHashSet<String> actors = new LinkedHashSet<>();
        auditRecords.stream()
                .map(SafetyAuditRecordResponse::actor)
                .forEach(actors::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("actions", auditRecords.stream().map(record -> record.action().name()).toList());
        summary.put("actors", List.copyOf(actors));
        summary.put("latestSummary", last.summary());
        if (bundle.run().approvalId() != null) {
            summary.put("approvalId", bundle.run().approvalId().toString());
        }

        return new RunTraceReferenceResponse(
                bundle.run().id() + ":audit",
                "audit",
                "Run audit lifecycle",
                bundle.run().status(),
                first.recordedAt(),
                last.recordedAt(),
                auditRecords.size(),
                "/safety/audit-records?resourceType=run&resourceId=" + bundle.run().id(),
                summary
        );
    }

    private RunTraceReferenceResponse toTelemetryTrace(ObservationBundle bundle) {
        List<LatencyTelemetrySnapshotResponse> telemetry = bundle.telemetry();
        LatencyTelemetrySnapshotResponse first = telemetry.get(0);
        LatencyTelemetrySnapshotResponse last = telemetry.get(telemetry.size() - 1);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("phases", telemetry.stream().map(snapshot -> snapshot.phase().name()).toList());
        summary.put("rollbackVerified", last.rollbackVerified());
        summary.put("latestMessage", last.message());
        if (bundle.run().latencyMilliseconds() != null) {
            summary.put("configuredLatencyMilliseconds", bundle.run().latencyMilliseconds());
        }
        if (bundle.run().latencyJitterMilliseconds() != null) {
            summary.put("configuredLatencyJitterMilliseconds", bundle.run().latencyJitterMilliseconds());
        }
        if (bundle.run().latencyMinimumMilliseconds() != null) {
            summary.put("configuredLatencyMinimumMilliseconds", bundle.run().latencyMinimumMilliseconds());
        }
        if (bundle.run().latencyMaximumMilliseconds() != null) {
            summary.put("configuredLatencyMaximumMilliseconds", bundle.run().latencyMaximumMilliseconds());
        }
        if (bundle.run().errorCode() != null) {
            summary.put("configuredErrorCode", bundle.run().errorCode());
        }
        if (bundle.run().trafficPercentage() != null) {
            summary.put("configuredTrafficPercentage", bundle.run().trafficPercentage());
        }
        if (bundle.run().dropPercentage() != null) {
            summary.put("configuredDropPercentage", bundle.run().dropPercentage());
        }
        if (!bundle.run().routeFilters().isEmpty()) {
            summary.put("configuredRouteFilters", bundle.run().routeFilters());
        }

        return new RunTraceReferenceResponse(
                bundle.run().id() + ":telemetry",
                "telemetry",
                "Run telemetry lifecycle",
                bundle.run().status(),
                first.capturedAt(),
                last.capturedAt(),
                telemetry.size(),
                "/safety/runs/" + bundle.run().id() + "/telemetry",
                summary
        );
    }

    private Instant runStartedAt(ChaosRun run) {
        return run.startedAt() == null ? run.createdAt() : run.startedAt();
    }

    private Instant terminalObservedAt(ChaosRun run) {
        if (run.rollbackVerifiedAt() != null) {
            return run.rollbackVerifiedAt();
        }
        return run.endedAt();
    }

    private Instant maxInstant(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private record ObservationBundle(
            ChaosRun run,
            List<LatencyTelemetrySnapshotResponse> telemetry,
            List<SafetyAuditRecordResponse> auditRecords
    ) {
    }
}
