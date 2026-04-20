package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.agents.api.AgentCommandResponse;
import com.myg.controlplane.agents.api.AgentResponse;
import com.myg.controlplane.agents.domain.AgentCommandStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);

    private final AgentRuntimeProperties properties;
    private final AgentControlPlaneClient agentControlPlaneClient;
    private final AgentIdentityStore identityStore;
    private final TaskScheduler taskScheduler;
    private final FaultExecutionEngine faultExecutionEngine;
    private final Clock clock;

    private final Map<UUID, ActiveCommandExecution> activeExecutions = new HashMap<>();
    private final Map<UUID, TerminalCommandState> terminalExecutions = new HashMap<>();

    private volatile PersistedAgentIdentity currentIdentity;
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile ScheduledFuture<?> commandPollTask;

    public AgentRuntimeService(AgentRuntimeProperties properties,
                               AgentControlPlaneClient agentControlPlaneClient,
                               AgentIdentityStore identityStore,
                               TaskScheduler taskScheduler,
                               FaultExecutionEngine faultExecutionEngine,
                               Clock clock) {
        this.properties = properties;
        this.agentControlPlaneClient = agentControlPlaneClient;
        this.identityStore = identityStore;
        this.taskScheduler = taskScheduler;
        this.faultExecutionEngine = faultExecutionEngine;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void bootstrapOnStartup() {
        if (!properties.isEnabled()) {
            return;
        }
        ensureRegistered();
        if (heartbeatTask == null) {
            heartbeatTask = taskScheduler.scheduleWithFixedDelay(this::sendHeartbeat, properties.getHeartbeatInterval());
        }
        if (commandPollTask == null) {
            commandPollTask = taskScheduler.scheduleWithFixedDelay(this::pollCommands, properties.getCommandPollInterval());
        }
    }

    public void sendHeartbeat() {
        if (!properties.isEnabled()) {
            return;
        }

        PersistedAgentIdentity identity = ensureRegistered();
        if (identity == null) {
            return;
        }

        try {
            AgentResponse response = agentControlPlaneClient.heartbeat(identity.id());
            log.info(
                    "agent_runtime_event=heartbeat_ok agentId={} status={} environment={} region={} lastHeartbeatAt={}",
                    response.id(),
                    response.status(),
                    response.environment(),
                    response.region(),
                    response.lastHeartbeatAt()
            );
        } catch (AgentIdentityRejectedException exception) {
            log.warn(
                    "agent_runtime_event=heartbeat_rejected agentId={} action=reregister reason=identity_not_found",
                    exception.getAgentId()
            );
            clearCurrentIdentity();
            registerAndPersist("heartbeat_reregistered");
        } catch (RuntimeException exception) {
            log.warn(
                    "agent_runtime_event=heartbeat_failed agentId={} message={}",
                    identity.id(),
                    exception.getMessage()
            );
        }
    }

    public void pollCommands() {
        if (!properties.isEnabled()) {
            return;
        }

        PersistedAgentIdentity identity = ensureRegistered();
        if (identity == null) {
            return;
        }

        try {
            Optional<AgentCommandResponse> assignment = agentControlPlaneClient.pollNextCommand(identity.id());
            assignment.ifPresent(command -> handlePolledCommand(identity.id(), command));
        } catch (AgentIdentityRejectedException exception) {
            log.warn(
                    "agent_runtime_event=command_poll_rejected agentId={} action=reregister",
                    exception.getAgentId()
            );
            clearCurrentIdentity();
            registerAndPersist("command_poll_reregistered");
        } catch (RuntimeException exception) {
            log.warn(
                    "agent_runtime_event=command_poll_failed agentId={} message={}",
                    identity.id(),
                    exception.getMessage()
            );
        }
    }

    private void handlePolledCommand(UUID agentId, AgentCommandResponse command) {
        if (command.status() == AgentCommandStatus.STOP_REQUESTED) {
            stopCommand(agentId, command.id());
            return;
        }

        TerminalCommandState terminalState = terminalState(command.id());
        if (terminalState != null) {
            if (command.status() != terminalState.status()) {
                reportStateSafely(agentId, command.id(), terminalState.status(), terminalState.message());
            }
            return;
        }

        if (hasActiveExecution(command.id())) {
            if (command.status() != AgentCommandStatus.RUNNING) {
                reportStateSafely(agentId, command.id(), AgentCommandStatus.RUNNING, "Command already running on agent");
            }
            return;
        }

        reportCommandState(agentId, command.id(), AgentCommandStatus.RECEIVED, "Command received by agent runtime");

        FaultExecutionHandle handle = null;
        ScheduledFuture<?> completionTask = null;
        try {
            handle = faultExecutionEngine.apply(command);
            completionTask = taskScheduler.schedule(
                    () -> completeCommand(agentId, command.id()),
                    Instant.now(clock).plusSeconds(command.durationSeconds())
            );
            if (completionTask == null) {
                throw new IllegalStateException("Task scheduler rejected command completion task");
            }
            storeActiveExecution(command.id(), new ActiveCommandExecution(handle, completionTask));
            reportStateSafely(agentId, command.id(), AgentCommandStatus.RUNNING, "Fault injection is active");
        } catch (RuntimeException exception) {
            if (completionTask != null) {
                completionTask.cancel(false);
            }
            cleanupAfterStartFailure(handle, exception);
            TerminalCommandState failedState = storeTerminalState(
                    command.id(),
                    AgentCommandStatus.FAILED,
                    "Failed to start fault injection: " + exception.getMessage()
            );
            reportStateSafely(agentId, command.id(), failedState.status(), failedState.message());
        }
    }

    private void cleanupAfterStartFailure(FaultExecutionHandle handle, RuntimeException startFailure) {
        if (handle == null) {
            return;
        }
        try {
            handle.stop();
        } catch (RuntimeException cleanupFailure) {
            startFailure.addSuppressed(cleanupFailure);
        }
    }

    private void stopCommand(UUID agentId, UUID commandId) {
        TerminalCommandState terminalState = terminalState(commandId);
        if (terminalState != null) {
            reportStateSafely(agentId, commandId, terminalState.status(), terminalState.message());
            return;
        }

        ActiveCommandExecution activeExecution = removeActiveExecution(commandId);
        if (activeExecution == null) {
            TerminalCommandState stopped = storeTerminalState(
                    commandId,
                    AgentCommandStatus.STOPPED,
                    "Stop acknowledged before the command was applied"
            );
            reportStateSafely(agentId, commandId, stopped.status(), stopped.message());
            return;
        }

        activeExecution.completionTask().cancel(false);
        try {
            activeExecution.handle().stop();
            TerminalCommandState stopped = storeTerminalState(
                    commandId,
                    AgentCommandStatus.STOPPED,
                    "Fault injection stopped by control plane request"
            );
            reportStateSafely(agentId, commandId, stopped.status(), stopped.message());
        } catch (RuntimeException exception) {
            TerminalCommandState failed = storeTerminalState(
                    commandId,
                    AgentCommandStatus.FAILED,
                    "Failed while stopping fault injection: " + exception.getMessage()
            );
            reportStateSafely(agentId, commandId, failed.status(), failed.message());
        }
    }

    private void completeCommand(UUID agentId, UUID commandId) {
        ActiveCommandExecution activeExecution = removeActiveExecution(commandId);
        if (activeExecution == null) {
            return;
        }

        try {
            activeExecution.handle().stop();
            TerminalCommandState completed = storeTerminalState(
                    commandId,
                    AgentCommandStatus.COMPLETED,
                    "Fault injection completed after requested duration"
            );
            reportStateSafely(agentId, commandId, completed.status(), completed.message());
        } catch (RuntimeException exception) {
            TerminalCommandState failed = storeTerminalState(
                    commandId,
                    AgentCommandStatus.FAILED,
                    "Failed during fault cleanup: " + exception.getMessage()
            );
            reportStateSafely(agentId, commandId, failed.status(), failed.message());
        }
    }

    private void reportCommandState(UUID agentId, UUID commandId, AgentCommandStatus status, String message) {
        agentControlPlaneClient.reportCommandState(agentId, commandId, status, message);
    }

    private void reportStateSafely(UUID agentId, UUID commandId, AgentCommandStatus status, String message) {
        try {
            reportCommandState(agentId, commandId, status, message);
        } catch (RuntimeException exception) {
            log.warn(
                    "agent_runtime_event=command_state_report_failed agentId={} commandId={} status={} message={}",
                    agentId,
                    commandId,
                    status,
                    exception.getMessage()
            );
        }
    }

    private PersistedAgentIdentity ensureRegistered() {
        PersistedAgentIdentity identity = currentIdentity;
        if (identity != null) {
            return identity;
        }

        synchronized (this) {
            if (currentIdentity != null) {
                return currentIdentity;
            }

            Optional<PersistedAgentIdentity> persistedIdentity = identityStore.load();
            if (persistedIdentity.isPresent()) {
                PersistedAgentIdentity persisted = persistedIdentity.get();
                currentIdentity = persisted;
                try {
                    AgentResponse response = agentControlPlaneClient.heartbeat(persisted.id());
                    log.info(
                            "agent_runtime_event=identity_reused agentId={} status={} environment={} region={}",
                            response.id(),
                            response.status(),
                            response.environment(),
                            response.region()
                    );
                    return persisted;
                } catch (AgentIdentityRejectedException exception) {
                    log.warn(
                            "agent_runtime_event=bootstrap_identity_rejected agentId={} action=register",
                            exception.getAgentId()
                    );
                    currentIdentity = null;
                } catch (RuntimeException exception) {
                    log.warn(
                            "agent_runtime_event=bootstrap_heartbeat_failed agentId={} message={}",
                            persisted.id(),
                            exception.getMessage()
                    );
                    return persisted;
                }
            }

            return registerAndPersist("registered");
        }
    }

    private PersistedAgentIdentity registerAndPersist(String event) {
        try {
            AgentResponse response = agentControlPlaneClient.register();
            PersistedAgentIdentity identity = PersistedAgentIdentity.from(response);
            identityStore.save(identity);
            currentIdentity = identity;
            log.info(
                    "agent_runtime_event={} agentId={} name={} environment={} region={} capabilities={}",
                    event,
                    identity.id(),
                    identity.name(),
                    identity.environment(),
                    identity.region(),
                    identity.supportedFaultCapabilities()
            );
            return identity;
        } catch (RuntimeException exception) {
            log.warn(
                    "agent_runtime_event=registration_failed controlPlaneBaseUrl={} message={}",
                    properties.getControlPlaneBaseUrl(),
                    exception.getMessage()
            );
            return null;
        }
    }

    private synchronized void clearCurrentIdentity() {
        currentIdentity = null;
    }

    private synchronized boolean hasActiveExecution(UUID commandId) {
        return activeExecutions.containsKey(commandId);
    }

    private synchronized void storeActiveExecution(UUID commandId, ActiveCommandExecution execution) {
        activeExecutions.put(commandId, execution);
    }

    private synchronized ActiveCommandExecution removeActiveExecution(UUID commandId) {
        return activeExecutions.remove(commandId);
    }

    private synchronized TerminalCommandState terminalState(UUID commandId) {
        return terminalExecutions.get(commandId);
    }

    private synchronized TerminalCommandState storeTerminalState(UUID commandId,
                                                                 AgentCommandStatus status,
                                                                 String message) {
        TerminalCommandState terminalState = new TerminalCommandState(status, message);
        terminalExecutions.put(commandId, terminalState);
        return terminalState;
    }

    private record ActiveCommandExecution(FaultExecutionHandle handle, ScheduledFuture<?> completionTask) {
    }

    private record TerminalCommandState(AgentCommandStatus status, String message) {
    }
}
