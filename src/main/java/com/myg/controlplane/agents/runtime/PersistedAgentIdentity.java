package com.myg.controlplane.agents.runtime;

import com.myg.controlplane.agents.api.AgentResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersistedAgentIdentity(
        UUID id,
        String name,
        String hostname,
        String environment,
        String region,
        List<String> supportedFaultCapabilities,
        Instant registeredAt
) {
    public PersistedAgentIdentity {
        supportedFaultCapabilities = List.copyOf(supportedFaultCapabilities);
    }

    public static PersistedAgentIdentity from(AgentResponse response) {
        return new PersistedAgentIdentity(
                response.id(),
                response.name(),
                response.hostname(),
                response.environment(),
                response.region(),
                response.supportedFaultCapabilities(),
                response.registeredAt()
        );
    }
}
