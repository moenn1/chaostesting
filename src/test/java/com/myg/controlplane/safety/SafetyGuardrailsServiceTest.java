package com.myg.controlplane.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SafetyGuardrailsServiceTest {

    private final DispatchApprovalService dispatchApprovalService = Mockito.mock(DispatchApprovalService.class);
    private final KillSwitchService killSwitchService = Mockito.mock(KillSwitchService.class);
    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);

    private SafetyGuardrailsProperties properties;
    private LatencyInjectionProperties latencyInjectionProperties;
    private SafetyGuardrailsService safetyGuardrailsService;

    @BeforeEach
    void setUp() {
        properties = new SafetyGuardrailsProperties();
        properties.setEnvironmentPolicyMode(EnvironmentPolicyMode.ALLOWLIST);
        properties.setControlledEnvironments(List.of("dev", "staging", "prod"));
        properties.setProductionLikeEnvironments(List.of("prod"));
        properties.setMaxDuration(Duration.ofMinutes(15));
        latencyInjectionProperties = new LatencyInjectionProperties();
        latencyInjectionProperties.setMaxLatency(Duration.ofSeconds(5));
        safetyGuardrailsService = new SafetyGuardrailsService(
                Clock.fixed(Instant.parse("2026-04-20T16:00:00Z"), ZoneOffset.UTC),
                properties,
                latencyInjectionProperties,
                dispatchApprovalService,
                killSwitchService,
                auditLogService
        );
        when(killSwitchService.isEnabled()).thenReturn(false);
    }

    @Test
    void rejectsDispatchesWhileKillSwitchIsEnabled() {
        when(killSwitchService.isEnabled()).thenReturn(true);

        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 120, 250, null, null, null, null, 30, null,
                        List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.KILL_SWITCH_ACTIVE);
    }

    @Test
    void rejectsEnvironmentOutsideAllowlist() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("qa", "payments", "latency", 60, 250, null, null, null, null, 30, null,
                        List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.ENVIRONMENT_NOT_ALLOWED);
    }

    @Test
    void rejectsConfiguredDenylistEnvironment() {
        properties.setEnvironmentPolicyMode(EnvironmentPolicyMode.DENYLIST);
        properties.setControlledEnvironments(List.of("prod"));

        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("prod", "payments", "latency", 60, 250, null, null, null, null, 30, null,
                        List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(
                        GuardrailViolationCode.ENVIRONMENT_NOT_ALLOWED,
                        GuardrailViolationCode.APPROVAL_REQUIRED
                );
    }

    @Test
    void marksProductionHttpErrorDispatchesAsApprovalRequired() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("prod", "checkout", "http_error", 120, null, null, null, null, 503, 25, null,
                        List.of("/checkout"), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.APPROVAL_REQUIRED);
        assertThat(response.requiresApproval()).isTrue();
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.APPROVAL_REQUIRED);
    }

    @Test
    void rejectsDispatchesThatExceedMaxDuration() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 60 * 20L, 250, null, null, null, null, 30,
                        null, List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.MAX_DURATION_EXCEEDED);
    }

    @Test
    void rejectsHttpErrorDispatchesMissingRequiredFaultConfig() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "http_error", 120, null, null, null, null, null, null,
                        null, List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(
                        GuardrailViolationCode.HTTP_ERROR_CODE_REQUIRED,
                        GuardrailViolationCode.TRAFFIC_PERCENTAGE_REQUIRED
                );
    }

    @Test
    void rejectsUnsupportedHttpErrorCodes() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "http_error", 120, null, null, null, null, 504, 15,
                        null, List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.HTTP_ERROR_CODE_UNSUPPORTED);
    }

    @Test
    void allowsProductionLikeDispatchWithValidApproval() {
        UUID approvalId = UUID.randomUUID();
        when(dispatchApprovalService.isActiveFor(approvalId, "prod")).thenReturn(true);

        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("prod", "checkout", "http_error", 120, null, null, null, null, 500, 10, null,
                        List.of("/checkout"), approvalId, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.ALLOWED);
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void rejectsProductionLikeDispatchWithExpiredApproval() {
        UUID approvalId = UUID.randomUUID();
        when(dispatchApprovalService.isActiveFor(approvalId, "prod")).thenReturn(false);

        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("prod", "checkout", "http_error", 120, null, null, null, null, 503, 20, null,
                        List.of("/checkout"), approvalId, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.APPROVAL_INVALID);
    }

    @Test
    void rejectsLatencyDispatchWithoutLatencyAmountOrTrafficScope() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 120, null, null, null, null, null, null,
                        null, List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(
                        GuardrailViolationCode.LATENCY_AMOUNT_REQUIRED,
                        GuardrailViolationCode.TRAFFIC_PERCENTAGE_REQUIRED
                );
    }

    @Test
    void rejectsLatencyDispatchThatExceedsConfiguredLatencyCeiling() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 120, 6000, null, null, null, null, 30, null,
                        List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.maxLatencyMilliseconds()).isEqualTo(5000);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.LATENCY_AMOUNT_EXCEEDED);
    }

    @Test
    void allowsLatencyDispatchWithJitter() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 120, 250, 50, null, null, null, 30, null,
                        List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.ALLOWED);
        assertThat(response.latencyJitterMilliseconds()).isEqualTo(50);
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void allowsLatencyDispatchWithBounds() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 120, null, null, 100, 300, null, 30, null,
                        List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.ALLOWED);
        assertThat(response.latencyMinimumMilliseconds()).isEqualTo(100);
        assertThat(response.latencyMaximumMilliseconds()).isEqualTo(300);
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void rejectsLatencyDispatchWhenBoundsAndFixedLatencyAreMixed() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 120, 250, null, 100, 300, null, 30, null,
                        List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .contains(GuardrailViolationCode.LATENCY_CONFIGURATION_CONFLICT);
    }

    @Test
    void rejectsRequestDropDispatchWithoutDropPercentage() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "request_drop", 120, null, null, null, null, null, null,
                        null, List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.DROP_PERCENTAGE_REQUIRED);
    }

    @Test
    void allowsRequestDropDispatchWithinSupportedPercentageRange() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "request_drop", 120, null, null, null, null, null, null,
                        15, List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.ALLOWED);
        assertThat(response.dropPercentage()).isEqualTo(15);
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void allowsProcessKillAndServicePauseDispatchesWithoutLatencySettings() {
        DispatchValidationResponse processKill = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout-worker", "process_kill", 120, null, null, null, null,
                        null, null, null, List.of(), null, "operator-a")
        );
        DispatchValidationResponse servicePause = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "inventory-daemon", "service_pause", 180, null, null, null, null,
                        null, null, null, List.of(), null, "operator-a")
        );

        assertThat(processKill.decision()).isEqualTo(DispatchDecision.ALLOWED);
        assertThat(processKill.violations()).isEmpty();
        assertThat(servicePause.decision()).isEqualTo(DispatchDecision.ALLOWED);
        assertThat(servicePause.violations()).isEmpty();
    }

    @Test
    void rejectsUnsupportedManualDispatchFaultTypes() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "inventory-daemon", "consumer_pause", 180, null, null, null, null,
                        null, null, null, List.of(), null, "operator-a")
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.UNSUPPORTED_FAULT_TYPE);
    }
}
