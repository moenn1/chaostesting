package com.myg.controlplane.agents.runtime;

import java.util.UUID;

public class AgentIdentityRejectedException extends RuntimeException {

    private final UUID agentId;

    public AgentIdentityRejectedException(UUID agentId, Throwable cause) {
        super("Control plane no longer recognizes agent " + agentId, cause);
        this.agentId = agentId;
    }

    public UUID getAgentId() {
        return agentId;
    }
}
