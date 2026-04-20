package com.myg.controlplane.agents.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myg.controlplane.agents.api.AgentCommandResponse;
import com.myg.controlplane.agents.api.AgentResponse;
import com.myg.controlplane.agents.domain.AgentCommandStatus;
import com.myg.controlplane.agents.domain.AgentStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.scheduling.TaskScheduler;

class AgentRuntimeServiceTest {

    private final AgentControlPlaneClient agentControlPlaneClient = Mockito.mock(AgentControlPlaneClient.class);
    private final AgentIdentityStore agentIdentityStore = Mockito.mock(AgentIdentityStore.class);
    private final TaskScheduler taskScheduler = Mockito.mock(TaskScheduler.class);
    private final FaultExecutionEngine faultExecutionEngine = Mockito.mock(FaultExecutionEngine.class);
    private final FaultExecutionHandle faultExecutionHandle = Mockito.mock(FaultExecutionHandle.class);
    private final ScheduledFuture<Object> recurringFuture = Mockito.mock(ScheduledFuture.class);
    private final ScheduledFuture<Object> completionFuture = Mockito.mock(ScheduledFuture.class);

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-20T16:00:00Z"), ZoneOffset.UTC);

    private AgentRuntimeProperties properties;
    private AgentRuntimeService agentRuntimeService;
    private Runnable completionRunnable;

    @BeforeEach
    void setUp() {
        properties = new AgentRuntimeProperties();
        properties.setEnabled(true);
        properties.setName("agent-runtime");
        properties.setHostname("node-a.internal");
        properties.setEnvironment("staging");
        properties.setRegion("eu-west-1");
        properties.setSupportedFaultCapabilities(List.of("latency", "http_error"));
        properties.setHeartbeatInterval(Duration.ofSeconds(10));
        properties.setCommandPollInterval(Duration.ofSeconds(5));

        doReturn(recurringFuture).when(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            completionRunnable = invocation.getArgument(0);
            return completionFuture;
        });

        agentRuntimeService = new AgentRuntimeService(
                properties,
                agentControlPlaneClient,
                agentIdentityStore,
                taskScheduler,
                faultExecutionEngine,
                clock
        );
    }

    @Test
    void bootstrapRegistersAndSchedulesHeartbeatAndPolling() {
        AgentResponse registration = agentResponse(UUID.randomUUID());
        when(agentIdentityStore.load()).thenReturn(Optional.empty());
        when(agentControlPlaneClient.register()).thenReturn(registration);

        agentRuntimeService.bootstrapOnStartup();

        verify(agentControlPlaneClient).register();
        verify(agentIdentityStore).save(PersistedAgentIdentity.from(registration));
        verify(taskScheduler, times(2)).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
    }

    @Test
    void redeliveredCommandStartsOnlyOnceAndReportsRunningOnce() {
        UUID agentId = UUID.randomUUID();
        AgentResponse registration = agentResponse(agentId);
        AgentCommandResponse assignment = commandResponse(UUID.randomUUID(), agentId, AgentCommandStatus.ASSIGNED, 30);

        when(agentIdentityStore.load()).thenReturn(Optional.empty());
        when(agentControlPlaneClient.register()).thenReturn(registration);
        when(agentControlPlaneClient.pollNextCommand(agentId)).thenReturn(Optional.of(assignment), Optional.of(assignment));
        when(faultExecutionEngine.apply(assignment)).thenReturn(faultExecutionHandle);

        agentRuntimeService.bootstrapOnStartup();
        agentRuntimeService.pollCommands();
        agentRuntimeService.pollCommands();

        verify(faultExecutionEngine, times(1)).apply(assignment);
        verify(agentControlPlaneClient, times(1)).reportCommandState(
                eq(agentId),
                eq(assignment.id()),
                eq(AgentCommandStatus.RECEIVED),
                anyString()
        );
        verify(agentControlPlaneClient, atLeastOnce()).reportCommandState(
                eq(agentId),
                eq(assignment.id()),
                eq(AgentCommandStatus.RUNNING),
                anyString()
        );
    }

    @Test
    void stopRequestedCommandCleansUpOnceAndReportsStopped() {
        UUID agentId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        AgentResponse registration = agentResponse(agentId);
        AgentCommandResponse assignment = commandResponse(commandId, agentId, AgentCommandStatus.ASSIGNED, 60);
        AgentCommandResponse stopRequested = commandResponse(commandId, agentId, AgentCommandStatus.STOP_REQUESTED, 60);

        when(agentIdentityStore.load()).thenReturn(Optional.empty());
        when(agentControlPlaneClient.register()).thenReturn(registration);
        when(agentControlPlaneClient.pollNextCommand(agentId)).thenReturn(Optional.of(assignment), Optional.of(stopRequested));
        when(faultExecutionEngine.apply(assignment)).thenReturn(faultExecutionHandle);

        agentRuntimeService.bootstrapOnStartup();
        agentRuntimeService.pollCommands();
        agentRuntimeService.pollCommands();

        verify(faultExecutionEngine).apply(assignment);
        verify(faultExecutionHandle).stop();
        verify(completionFuture).cancel(false);
        verify(agentControlPlaneClient).reportCommandState(
                eq(agentId),
                eq(commandId),
                eq(AgentCommandStatus.STOPPED),
                anyString()
        );
    }

    @Test
    void scheduledCompletionStopsFaultAndReportsCompleted() {
        UUID agentId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        AgentResponse registration = agentResponse(agentId);
        AgentCommandResponse assignment = commandResponse(commandId, agentId, AgentCommandStatus.ASSIGNED, 45);

        when(agentIdentityStore.load()).thenReturn(Optional.empty());
        when(agentControlPlaneClient.register()).thenReturn(registration);
        when(agentControlPlaneClient.pollNextCommand(agentId)).thenReturn(Optional.of(assignment));
        when(faultExecutionEngine.apply(assignment)).thenReturn(faultExecutionHandle);

        agentRuntimeService.bootstrapOnStartup();
        agentRuntimeService.pollCommands();
        completionRunnable.run();

        verify(faultExecutionHandle).stop();
        verify(agentControlPlaneClient).reportCommandState(
                eq(agentId),
                eq(commandId),
                eq(AgentCommandStatus.COMPLETED),
                anyString()
        );
        verify(agentControlPlaneClient, never()).reportCommandState(
                eq(agentId),
                eq(commandId),
                eq(AgentCommandStatus.STOPPED),
                anyString()
        );
    }

    private AgentResponse agentResponse(UUID id) {
        return new AgentResponse(
                id,
                properties.getName(),
                properties.getHostname(),
                properties.getEnvironment(),
                properties.getRegion(),
                List.copyOf(properties.getSupportedFaultCapabilities()),
                Instant.parse("2026-04-20T16:00:00Z"),
                Instant.parse("2026-04-20T16:00:10Z"),
                AgentStatus.HEALTHY
        );
    }

    private AgentCommandResponse commandResponse(UUID commandId,
                                                 UUID agentId,
                                                 AgentCommandStatus status,
                                                 long durationSeconds) {
        return new AgentCommandResponse(
                commandId,
                agentId,
                "latency",
                Map.of("delayMs", "250"),
                durationSeconds,
                "checkout-service",
                status,
                1,
                Instant.parse("2026-04-20T16:00:00Z"),
                Instant.parse("2026-04-20T16:00:00Z"),
                null,
                null,
                Instant.parse("2026-04-20T16:00:01Z"),
                null
        );
    }
}
