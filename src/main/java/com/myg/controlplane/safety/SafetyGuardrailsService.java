package com.myg.controlplane.safety;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SafetyGuardrailsService {

    private final Clock clock;
    private final SafetyGuardrailsProperties properties;
    private final LatencyInjectionProperties latencyInjectionProperties;
    private final DispatchApprovalService dispatchApprovalService;
    private final KillSwitchService killSwitchService;
    private final AuditLogService auditLogService;

    public SafetyGuardrailsService(Clock clock,
                                   SafetyGuardrailsProperties properties,
                                   LatencyInjectionProperties latencyInjectionProperties,
                                   DispatchApprovalService dispatchApprovalService,
                                   KillSwitchService killSwitchService,
                                   AuditLogService auditLogService) {
        this.clock = clock;
        this.properties = properties;
        this.latencyInjectionProperties = latencyInjectionProperties;
        this.dispatchApprovalService = dispatchApprovalService;
        this.killSwitchService = killSwitchService;
        this.auditLogService = auditLogService;
    }

    public DispatchValidationResponse validate(RunDispatchRequest request) {
        return evaluate(request).toResponse(
                request,
                properties.getMaxDuration().toSeconds(),
                latencyInjectionProperties.getMaxLatency().toMillis()
        );
    }

    public DispatchAuthorizationResponse authorize(String requestedBy, RunDispatchRequest request) {
        DispatchValidationResponse response = validate(request);
        if (response.decision() != DispatchDecision.ALLOWED) {
            auditLogService.record(
                    SafetyAuditEventType.RUN_START_REJECTED,
                    AuditResourceType.RUN_DISPATCH,
                    UUID.randomUUID().toString(),
                    requestedBy.trim(),
                    "Run start rejected by safety guardrails",
                    rejectedDispatchMetadata(request, response),
                    clock.instant()
            );
            throw new RunDispatchRejectedException(response);
        }

        return new DispatchAuthorizationResponse(
                UUID.randomUUID(),
                "AUTHORIZED",
                response.targetEnvironment(),
                request.targetSelector().trim(),
                response.faultType(),
                request.requestedDurationSeconds(),
                request.latencyMilliseconds(),
                request.latencyJitterMilliseconds(),
                request.latencyMinimumMilliseconds(),
                request.latencyMaximumMilliseconds(),
                request.errorCode(),
                request.trafficPercentage(),
                request.dropPercentage(),
                request.routeFilters(),
                request.approvalId(),
                clock.instant()
        );
    }

    DispatchValidationResult evaluate(RunDispatchRequest request) {
        String normalizedEnvironment = properties.normalizeEnvironment(request.targetEnvironment());
        List<GuardrailViolation> violations = new ArrayList<>();

        if (killSwitchService.isEnabled()) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.KILL_SWITCH_ACTIVE,
                    "Global kill switch is enabled; new dispatches are blocked until the platform is re-enabled."
            ));
        }

        if (!isEnvironmentAllowed(normalizedEnvironment)) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.ENVIRONMENT_NOT_ALLOWED,
                    "Environment '%s' is blocked by the configured %s."
                            .formatted(normalizedEnvironment, properties.getEnvironmentPolicyMode().name().toLowerCase())
            ));
        }

        if (request.requestedDuration().compareTo(properties.getMaxDuration()) > 0) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.MAX_DURATION_EXCEEDED,
                    "Requested duration %ss exceeds the configured max of %ss."
                            .formatted(request.requestedDurationSeconds(), properties.getMaxDuration().toSeconds())
            ));
        }

        switch (request.normalizedFaultType()) {
            case "latency" -> evaluateLatencyConfiguration(request, violations);
            case "http_error" -> validateHttpErrorConfig(request, violations);
            case "request_drop" -> evaluateRequestDropConfiguration(request, violations);
            default -> {
            }
        }

        if (requiresApproval(normalizedEnvironment)) {
            if (request.approvalId() == null) {
                violations.add(new GuardrailViolation(
                        GuardrailViolationCode.APPROVAL_REQUIRED,
                        "Environment '%s' requires explicit approval before dispatch."
                                .formatted(normalizedEnvironment)
                ));
            } else if (!dispatchApprovalService.isActiveFor(request.approvalId(), normalizedEnvironment)) {
                violations.add(new GuardrailViolation(
                        GuardrailViolationCode.APPROVAL_INVALID,
                        "Approval '%s' is missing, expired, or not valid for environment '%s'."
                                .formatted(request.approvalId(), normalizedEnvironment)
                ));
            }
        }

        return new DispatchValidationResult(decide(violations), normalizedEnvironment, violations);
    }

    private void evaluateLatencyConfiguration(RunDispatchRequest request, List<GuardrailViolation> violations) {
        boolean usesFixedLatency = request.latencyMilliseconds() != null;
        boolean usesBoundedLatency = request.latencyMinimumMilliseconds() != null
                || request.latencyMaximumMilliseconds() != null;

        if (usesFixedLatency && usesBoundedLatency) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_CONFIGURATION_CONFLICT,
                    "Latency faults must use either latencyMilliseconds with optional latencyJitterMilliseconds or latencyMinimumMilliseconds/latencyMaximumMilliseconds bounds."
            ));
        }

        if (!usesFixedLatency && !usesBoundedLatency) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_AMOUNT_REQUIRED,
                    "Latency faults require latencyMilliseconds or latencyMinimumMilliseconds/latencyMaximumMilliseconds bounds."
            ));
        }

        if (usesFixedLatency) {
            evaluateFixedLatency(request, violations);
        }
        if (usesBoundedLatency) {
            evaluateLatencyBounds(request, violations);
        }

        validateTrafficPercentage(
                request.trafficPercentage(),
                violations,
                "Latency faults require a trafficPercentage between 1 and 100."
        );
    }

    private void evaluateFixedLatency(RunDispatchRequest request, List<GuardrailViolation> violations) {
        if (request.latencyMilliseconds() == null || request.latencyMilliseconds() <= 0) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_AMOUNT_REQUIRED,
                    "Latency faults require a positive latencyMilliseconds value."
            ));
            return;
        }

        long maxLatencyMillis = latencyInjectionProperties.getMaxLatency().toMillis();
        if (request.latencyMilliseconds() > maxLatencyMillis) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_AMOUNT_EXCEEDED,
                    "Latency amount %sms exceeds the configured max of %sms."
                            .formatted(request.latencyMilliseconds(), maxLatencyMillis)
            ));
        }

        if (request.latencyJitterMilliseconds() == null) {
            return;
        }
        if (request.latencyJitterMilliseconds() > request.latencyMilliseconds()) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_JITTER_INVALID,
                    "latencyJitterMilliseconds %sms cannot exceed latencyMilliseconds %sms."
                            .formatted(request.latencyJitterMilliseconds(), request.latencyMilliseconds())
            ));
        }
        if ((long) request.latencyMilliseconds() + request.latencyJitterMilliseconds() > maxLatencyMillis) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_JITTER_EXCEEDED,
                    "Latency jitter expands the upper bound to %sms, which exceeds the configured max of %sms."
                            .formatted(request.latencyMilliseconds() + request.latencyJitterMilliseconds(), maxLatencyMillis)
            ));
        }
    }

    private void evaluateLatencyBounds(RunDispatchRequest request, List<GuardrailViolation> violations) {
        if (request.latencyMinimumMilliseconds() == null || request.latencyMaximumMilliseconds() == null) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_BOUNDS_REQUIRED,
                    "Latency bounds require both latencyMinimumMilliseconds and latencyMaximumMilliseconds."
            ));
            return;
        }

        if (request.latencyMinimumMilliseconds() <= 0 || request.latencyMaximumMilliseconds() <= 0) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_BOUNDS_INVALID,
                    "Latency bounds must be greater than zero."
            ));
            return;
        }

        if (request.latencyMinimumMilliseconds() > request.latencyMaximumMilliseconds()) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_BOUNDS_INVALID,
                    "latencyMinimumMilliseconds %sms cannot exceed latencyMaximumMilliseconds %sms."
                            .formatted(request.latencyMinimumMilliseconds(), request.latencyMaximumMilliseconds())
            ));
        }
        if (request.latencyMaximumMilliseconds() > latencyInjectionProperties.getMaxLatency().toMillis()) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_AMOUNT_EXCEEDED,
                    "Latency bound %sms exceeds the configured max of %sms."
                            .formatted(request.latencyMaximumMilliseconds(), latencyInjectionProperties.getMaxLatency().toMillis())
            ));
        }
        if (request.latencyJitterMilliseconds() != null) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.LATENCY_CONFIGURATION_CONFLICT,
                    "latencyJitterMilliseconds cannot be combined with latencyMinimumMilliseconds/latencyMaximumMilliseconds bounds."
            ));
        }
    }

    private void validateHttpErrorConfig(RunDispatchRequest request, List<GuardrailViolation> violations) {
        if (request.errorCode() == null) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.HTTP_ERROR_CODE_REQUIRED,
                    "HTTP error injection requires an errorCode of 500 or 503."
            ));
        } else if (request.errorCode() != 500 && request.errorCode() != 503) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.HTTP_ERROR_CODE_UNSUPPORTED,
                    "HTTP error injection supports only 500 and 503 response codes."
            ));
        }

        validateTrafficPercentage(
                request.trafficPercentage(),
                violations,
                "Fault dispatches require a trafficPercentage between 1 and 100."
        );
    }

    private void evaluateRequestDropConfiguration(RunDispatchRequest request, List<GuardrailViolation> violations) {
        if (request.dropPercentage() == null || request.dropPercentage() <= 0) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.DROP_PERCENTAGE_REQUIRED,
                    "Request-drop faults require a dropPercentage between 1 and 100."
            ));
        } else if (request.dropPercentage() > 100) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.DROP_PERCENTAGE_INVALID,
                    "Drop percentage %s is outside the supported 1-100 range."
                            .formatted(request.dropPercentage())
            ));
        }
    }

    private void validateTrafficPercentage(Integer trafficPercentage,
                                           List<GuardrailViolation> violations,
                                           String missingMessage) {
        if (trafficPercentage == null || trafficPercentage <= 0) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.TRAFFIC_PERCENTAGE_REQUIRED,
                    missingMessage
            ));
        } else if (trafficPercentage > 100) {
            violations.add(new GuardrailViolation(
                    GuardrailViolationCode.TRAFFIC_PERCENTAGE_INVALID,
                    "Traffic percentage %s is outside the supported 1-100 range."
                            .formatted(trafficPercentage)
            ));
        }
    }

    private DispatchDecision decide(List<GuardrailViolation> violations) {
        if (violations.isEmpty()) {
            return DispatchDecision.ALLOWED;
        }

        boolean approvalOnly = violations.stream()
                .allMatch(violation -> violation.code() == GuardrailViolationCode.APPROVAL_REQUIRED);
        return approvalOnly ? DispatchDecision.APPROVAL_REQUIRED : DispatchDecision.REJECTED;
    }

    private boolean isEnvironmentAllowed(String normalizedEnvironment) {
        return switch (properties.getEnvironmentPolicyMode()) {
            case ALLOWLIST -> properties.normalizedControlledEnvironments().contains(normalizedEnvironment);
            case DENYLIST -> !properties.normalizedControlledEnvironments().contains(normalizedEnvironment);
        };
    }

    private boolean requiresApproval(String normalizedEnvironment) {
        return properties.normalizedProductionLikeEnvironments().contains(normalizedEnvironment);
    }

    private Map<String, Object> rejectedDispatchMetadata(RunDispatchRequest request,
                                                         DispatchValidationResponse response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("decision", response.decision().name());
        metadata.put("targetEnvironment", response.targetEnvironment());
        metadata.put("targetSelector", request.targetSelector().trim());
        metadata.put("faultType", request.normalizedFaultType());
        metadata.put("requestedDurationSeconds", request.requestedDurationSeconds());
        if (request.latencyMilliseconds() != null) {
            metadata.put("latencyMilliseconds", request.latencyMilliseconds());
        }
        if (request.latencyJitterMilliseconds() != null) {
            metadata.put("latencyJitterMilliseconds", request.latencyJitterMilliseconds());
        }
        if (request.latencyMinimumMilliseconds() != null) {
            metadata.put("latencyMinimumMilliseconds", request.latencyMinimumMilliseconds());
        }
        if (request.latencyMaximumMilliseconds() != null) {
            metadata.put("latencyMaximumMilliseconds", request.latencyMaximumMilliseconds());
        }
        if (request.errorCode() != null) {
            metadata.put("errorCode", request.errorCode());
        }
        if (request.trafficPercentage() != null) {
            metadata.put("trafficPercentage", request.trafficPercentage());
        }
        if (request.dropPercentage() != null) {
            metadata.put("dropPercentage", request.dropPercentage());
        }
        if (!request.routeFilters().isEmpty()) {
            metadata.put("routeFilters", request.routeFilters());
        }
        if (request.approvalId() != null) {
            metadata.put("approvalId", request.approvalId().toString());
        }
        metadata.put("violations", response.violations().stream()
                .map(violation -> Map.of(
                        "code", violation.code().name(),
                        "message", violation.message()
                ))
                .toList());
        return metadata;
    }
}
