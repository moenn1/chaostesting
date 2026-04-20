package com.myg.controlplane.agents.api;

import com.myg.controlplane.agents.domain.AgentCommandStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AgentCommandExecutionEventRequest(
        @NotNull UUID agentId,
        @NotNull AgentCommandStatus status,
        String message
) {
}
