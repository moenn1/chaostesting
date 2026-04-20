package com.myg.controlplane.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
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

    private SafetyGuardrailsProperties properties;
    private SafetyGuardrailsService safetyGuardrailsService;

    @BeforeEach
    void setUp() {
        properties = new SafetyGuardrailsProperties();
        properties.setEnvironmentPolicyMode(EnvironmentPolicyMode.ALLOWLIST);
        properties.setControlledEnvironments(List.of("dev", "staging", "prod"));
        properties.setProductionLikeEnvironments(List.of("prod"));
        properties.setMaxDuration(java.time.Duration.ofMinutes(15));
        safetyGuardrailsService = new SafetyGuardrailsService(
                Clock.fixed(Instant.parse("2026-04-20T16:00:00Z"), ZoneOffset.UTC),
                properties,
                dispatchApprovalService,
                killSwitchService
        );
        when(killSwitchService.isEnabled()).thenReturn(false);
    }

    @Test
    void rejectsDispatchesWhileKillSwitchIsEnabled() {
        when(killSwitchService.isEnabled()).thenReturn(true);

        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 120, null)
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.KILL_SWITCH_ACTIVE);
    }

    @Test
    void rejectsEnvironmentOutsideAllowlist() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("qa", "payments", "latency", 60, null)
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
                new RunDispatchRequest("prod", "payments", "latency", 60, null)
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(
                        GuardrailViolationCode.ENVIRONMENT_NOT_ALLOWED,
                        GuardrailViolationCode.APPROVAL_REQUIRED
                );
    }

    @Test
    void marksProductionLikeDispatchesAsApprovalRequired() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("prod", "checkout", "http_error", 120, null)
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.APPROVAL_REQUIRED);
        assertThat(response.requiresApproval()).isTrue();
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.APPROVAL_REQUIRED);
    }

    @Test
    void rejectsDispatchesThatExceedMaxDuration() {
        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("staging", "checkout", "latency", 60 * 20L, null)
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.MAX_DURATION_EXCEEDED);
    }

    @Test
    void allowsProductionLikeDispatchWithValidApproval() {
        UUID approvalId = UUID.randomUUID();
        when(dispatchApprovalService.isActiveFor(approvalId, "prod")).thenReturn(true);

        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("prod", "checkout", "latency", 120, approvalId)
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.ALLOWED);
        assertThat(response.violations()).isEmpty();
    }

    @Test
    void rejectsProductionLikeDispatchWithExpiredApproval() {
        UUID approvalId = UUID.randomUUID();
        when(dispatchApprovalService.isActiveFor(approvalId, "prod")).thenReturn(false);

        DispatchValidationResponse response = safetyGuardrailsService.validate(
                new RunDispatchRequest("prod", "checkout", "latency", 120, approvalId)
        );

        assertThat(response.decision()).isEqualTo(DispatchDecision.REJECTED);
        assertThat(response.violations()).extracting(GuardrailViolation::code)
                .containsExactly(GuardrailViolationCode.APPROVAL_INVALID);
    }
}
