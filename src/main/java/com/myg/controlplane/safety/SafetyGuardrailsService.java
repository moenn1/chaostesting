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
    private final DispatchApprovalService dispatchApprovalService;
    private final KillSwitchService killSwitchService;
    private final AuditLogService auditLogService;

    public SafetyGuardrailsService(Clock clock,
                                   SafetyGuardrailsProperties properties,
                                   DispatchApprovalService dispatchApprovalService,
                                   KillSwitchService killSwitchService,
                                   AuditLogService auditLogService) {
        this.clock = clock;
        this.properties = properties;
        this.dispatchApprovalService = dispatchApprovalService;
        this.killSwitchService = killSwitchService;
        this.auditLogService = auditLogService;
    }

    public DispatchValidationResponse validate(RunDispatchRequest request) {
        return evaluate(request).toResponse(request, properties.getMaxDuration().toSeconds());
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
                request.faultType().trim(),
                request.requestedDurationSeconds(),
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
        metadata.put("faultType", request.faultType().trim());
        metadata.put("requestedDurationSeconds", request.requestedDurationSeconds());
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
