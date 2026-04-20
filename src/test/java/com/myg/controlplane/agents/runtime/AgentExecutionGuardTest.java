package com.myg.controlplane.agents.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.myg.controlplane.safety.AuditLogService;
import com.myg.controlplane.safety.DispatchApprovalService;
import com.myg.controlplane.safety.EnvironmentPolicyMode;
import com.myg.controlplane.safety.KillSwitchService;
import com.myg.controlplane.safety.LatencyInjectionProperties;
import com.myg.controlplane.safety.SafetyGuardrailsProperties;
import com.myg.controlplane.safety.SafetyGuardrailsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentExecutionGuardTest {

    private final DispatchApprovalService dispatchApprovalService = Mockito.mock(DispatchApprovalService.class);
    private final KillSwitchService killSwitchService = Mockito.mock(KillSwitchService.class);
    private final AuditLogService auditLogService = Mockito.mock(AuditLogService.class);

    private AgentExecutionGuard agentExecutionGuard;

    @BeforeEach
    void setUp() {
        SafetyGuardrailsProperties properties = new SafetyGuardrailsProperties();
        properties.setEnvironmentPolicyMode(EnvironmentPolicyMode.ALLOWLIST);
        properties.setControlledEnvironments(List.of("staging", "prod"));
        properties.setProductionLikeEnvironments(List.of("prod"));
        properties.setMaxDuration(Duration.ofMinutes(15));
        LatencyInjectionProperties latencyInjectionProperties = new LatencyInjectionProperties();
        latencyInjectionProperties.setMaxLatency(Duration.ofSeconds(5));
        SafetyGuardrailsService safetyGuardrailsService = new SafetyGuardrailsService(
                Clock.fixed(Instant.parse("2026-04-20T16:00:00Z"), ZoneOffset.UTC),
                properties,
                latencyInjectionProperties,
                dispatchApprovalService,
                killSwitchService,
                auditLogService
        );
        when(killSwitchService.isEnabled()).thenReturn(false);
        agentExecutionGuard = new AgentExecutionGuard(safetyGuardrailsService);
    }

    @Test
    void rejectsExecutionPlansThatExceedTheConfiguredDurationLimit() {
        AgentExecutionPlan executionPlan = new AgentExecutionPlan(
                UUID.randomUUID(),
                "staging",
                "checkout",
                "latency",
                60 * 20L,
                250,
                null,
                null,
                null,
                30,
                null,
                null
        );

        assertThatThrownBy(() -> agentExecutionGuard.verify(executionPlan))
                .isInstanceOf(UnsafeExecutionPlanException.class);
    }

    @Test
    void allowsProductionExecutionPlansWhenApprovalIsStillValid() {
        UUID approvalId = UUID.randomUUID();
        when(dispatchApprovalService.isActiveFor(approvalId, "prod")).thenReturn(true);
        AgentExecutionPlan executionPlan = new AgentExecutionPlan(
                UUID.randomUUID(),
                "prod",
                "checkout",
                "latency",
                300,
                250,
                25,
                null,
                null,
                30,
                null,
                approvalId
        );

        assertThatCode(() -> agentExecutionGuard.verify(executionPlan))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsRequestDropExecutionPlansWithinSafetyLimits() {
        AgentExecutionPlan executionPlan = new AgentExecutionPlan(
                UUID.randomUUID(),
                "staging",
                "checkout",
                "request_drop",
                180,
                null,
                null,
                null,
                null,
                null,
                10,
                null
        );

        assertThatCode(() -> agentExecutionGuard.verify(executionPlan))
                .doesNotThrowAnyException();
    }
}
