package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.agents.api.AgentCommandResponse;
import com.myg.controlplane.agents.api.AgentResponse;
import com.myg.controlplane.agents.domain.AgentCommandStatus;
import java.util.Optional;
import java.util.UUID;

public interface AgentControlPlaneClient {

    AgentResponse register();

    AgentResponse heartbeat(UUID agentId);

    Optional<AgentCommandResponse> pollNextCommand(UUID agentId);

    AgentCommandResponse reportCommandState(UUID agentId, UUID commandId, AgentCommandStatus status, String message);
}
